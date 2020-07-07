package cn.smilehappiness.annotation;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.lang.annotation.*;

/**
 * <p>
 * 自定义，限流注解（默认一分钟，限流500次）
 * <p/>
 *
 * @author smilehappiness
 * @Date 2020/7/5 20:05
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface ApiLimit {

    /**
     * 限制某时间段内可以访问的次数，默认设置500
     */
    int limitCounts() default 500;

    /**
     * @return
     * @Description: 限制访问的某一个时间段，单位为秒，默认值1分钟
     */
    int timeSecond() default 60;
}
   