package cn.smilehappiness.aspect;

import cn.smilehappiness.annotation.ApiLimit;
import cn.smilehappiness.model.SmsMessage;
import com.alibaba.fastjson.JSON;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;


/**
 * Api Limit切面类
 *
 * @author smilehappiness
 * @Date 2020/7/5 19:55
 */
@Aspect
@Component
public class ApiLimitAspect {

    private Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 通过构造注入的方式，注入redisTemplate
     */
    private final RedisTemplate<String, Object> redisTemplate;

    public ApiLimitAspect(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * <p>
     * 定义切面表达式（类的维度拦截，可以拦截更多的方法）
     * <p/>
     *
     * @param
     * @return void
     * @Date 2020/7/5 20:07
     */
    @Pointcut("execution(* cn.smilehappiness..service..*Impl.*(..))")
    private void myPointCut() {
    }

    /**
     * <p>
     * 实现对添加ApiLimit注解的方法进行拦截，Limit处理（方式一：切面类拦截）
     * <p/>
     *
     * @param joinPoint
     * @return void
     * @Date 2020/7/5 20:10
     */
    //@Around("myPointCut()")
    public void requestLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        ApiLimit apiLimit = this.getAnnotation(joinPoint);
        //对业务方法进行全局限流
        if (apiLimit != null) {
            dealLimit(apiLimit, joinPoint, false);
        }
    }

    /**
     * <p>
     * 针对业务方法进行Limit限流处理，如果第一次请求被限制了，等待10秒后重试，如果再次失败，则抛出异常
     * <p/>
     *
     * @param apiLimit
     * @param joinPoint
     * @param flag      判断是否进行过一次重试了，如果重试过一次还是被限制，就抛异常
     * @return void
     * @Date 2020/7/5 20:30
     */
    private void dealLimit(ApiLimit apiLimit, ProceedingJoinPoint joinPoint, Boolean flag) throws Throwable {
        String msgKey = checkParam(apiLimit, joinPoint);
        //业务方法中，参数唯一key
        String cacheKey = "smsService:sendLimit:" + msgKey;

        ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();
        long methodCounts = valueOperations.increment(cacheKey, 1);
        // 如果该key不存在，则从0开始计算，并且当count为1的时候，设置过期时间
        if (methodCounts == 1) {
            redisTemplate.expire(cacheKey, apiLimit.timeSecond(), TimeUnit.SECONDS);
        }

        // 如果redis中的count大于限制的次数，则等待10秒重试
        if (methodCounts > apiLimit.limitCounts()) {
            if (!flag) {
                //等待10秒后，第一次重试
                Thread.sleep(10 * 1000);
                logger.warn("等待10秒后，第一次重试...");

                // 递归，再次请求业务方法
                dealLimit(apiLimit, joinPoint, true);
            } else {
                //如果第一次请求被限制了，等待10秒后重试，如果再次失败，则抛出异常，当然，如果不需要重试，直接抛异常或者逻辑处理即可
                throw new RuntimeException("短信发送三方Api接口超限，请30秒后再试！");
            }
        } else {
            //正常执行业务方法
            joinPoint.proceed();
        }

    }

    /**
     * <p>
     * 业务限流方法，业务参数非空检验
     * <p/>
     *
     * @param apiLimit
     * @param joinPoint
     * @return java.lang.String
     * @Date 2020/7/5 20:40
     */
    private String checkParam(ApiLimit apiLimit, ProceedingJoinPoint joinPoint) {
        if (apiLimit == null) {
            throw new RuntimeException("限流方法dealLimit处理异常!");
        }

        //获取方法的参数，通过参数设置缓存的唯一key
        Object[] args = joinPoint.getArgs();
        if (args == null) {
            throw new RuntimeException("业务方法参数不允许为空！");
        }

        SmsMessage smsMessage = null;
        if (args[0] instanceof SmsMessage) {
            smsMessage = (SmsMessage) args[0];
        }
        if (smsMessage == null) {
            throw new RuntimeException("HttpServletRequest请求，参数异常!");
        }

        // 获取HttpRequest
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new RuntimeException("HttpServletRequest请求，attributes参数异常!");
        }

        HttpServletRequest request = attributes.getRequest();
        // 判断request不能为空
        if (request == null) {
            throw new RuntimeException("HttpServletRequest请求，参数异常!");
        }

        String ip = request.getRemoteAddr();
        String uri = request.getRequestURI();
        logger.debug("请求的参数ip：【{}】, uri：【{}】,请求参数：【{}】", ip, uri, JSON.toJSONString(smsMessage));

        return smsMessage.getMsgKey();
    }

    /**
     * <p>
     * 环绕通知，对使用ApiLimit注解的方法进行拦截，限流处理（方式二：直接对注解进行拦截）
     * <p/>
     *
     * @param joinPoint
     * @return void
     * @Date 2020/7/5 21:03
     */
    @Around("@annotation(cn.smilehappiness.annotation.ApiLimit)")
    public void requestLimitByAnnotation(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        if (method == null) {
            return;
        }

        if (method.isAnnotationPresent(ApiLimit.class)) {
            dealLimit(method.getAnnotation(ApiLimit.class), joinPoint, false);
        }
    }

    /**
     * <p>
     * 获取注解
     * <p/>
     *
     * @param joinPoint
     * @return cn.smilehappiness.annotation.ApiLimit
     * @Date 2020/7/5 21:45
     */
    private ApiLimit getAnnotation(JoinPoint joinPoint) {
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();
        if (method == null) {
            return null;
        }

        return method.getAnnotation(ApiLimit.class);
    }
}