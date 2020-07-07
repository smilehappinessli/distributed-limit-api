package cn.smilehappiness;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(scanBasePackages = "cn.smilehappiness", exclude = {DataSourceAutoConfiguration.class})
public class DistributedBootStrap {

    public static void main(String[] args) {
        SpringApplication.run(DistributedBootStrap.class, args);
        System.out.println("[DISTRIBUTED-SERVER]服务启动完成!!!");
    }

}
