package cn.smilehappiness.distributelimit;

import cn.smilehappiness.model.SmsMessage;
import cn.smilehappiness.service.SmsMessageService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.*;

/**
 * <p>
 * 消息发送服务，限流功能测试
 * <p/>
 *
 * @author smilehappiness
 * @Date 2020/7/5 22:20
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class SmsMessageServiceTest {

    @Autowired
    private SmsMessageService smsMessageService;

    /**
     * <p>
     * 消息发送服务，限流功能测试-单条不会限流
     * <p/>
     *
     * @param
     * @return void
     * @Date 2020/7/5 22:25
     */
    @Test
    public void testSmsMessageSend() {
        SmsMessage smsMessage = new SmsMessage();
        smsMessage.setMsgKey("register-user");
        smsMessage.setContent("register an user notice!");
        smsMessageService.sendSmsMessage(smsMessage);
    }

    /**
     * <p>
     * 消息发送服务，限流功能测试（超过限定次数就会限流）
     * <p/>
     *
     * @param
     * @return void
     * @Date 2020/7/5 22:45
     */
    @Test
    public void testSmsMessageSendBatch() {
        //循环次数
        int count = 15;

        int corePoolSize = Runtime.getRuntime().availableProcessors() * 2;
        ThreadFactory nameThreadFactory = new ThreadFactoryBuilder().setNameFormat("smsLimit-pool-%d").build();
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, corePoolSize * 2 + 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1000), nameThreadFactory);
        CountDownLatch countDownLatch = new CountDownLatch(count);

        //注意：这里暂不使用多线程测试，因为拿不到HttpServletRequest请求参数，实际部署后或者你服务启动后，是有的，只不过这里测试用例没有
        for (int i = 0; i < count; i++) {
//            threadPoolExecutor.execute(() -> {
                SmsMessage smsMessage = new SmsMessage();
                smsMessage.setMsgKey("register-user");
                smsMessage.setContent("register an user notice!");
                //业务方法，添加了@ApiLimit(limitCounts = 10, timeSecond = 120)注解，表示2分钟只允许10次调用，否则就会限流
                smsMessageService.sendSmsMessage(smsMessage);

//                countDownLatch.countDown();
//            });
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("处理完成啦...");
        threadPoolExecutor.shutdown();
    }

}
