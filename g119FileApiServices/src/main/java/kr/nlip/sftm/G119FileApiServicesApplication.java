package kr.nlip.sftm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import kr.nlip.sftm.controller.config.FileUploadProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    FileUploadProperties.class
})
public class G119FileApiServicesApplication {
	public static void main(String[] args) {
		SpringApplication.run(G119FileApiServicesApplication.class, args);
	}

}
