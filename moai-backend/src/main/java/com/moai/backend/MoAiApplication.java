package com.moai.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing // JPA 감시자(Auditing) 기능 활성화
@SpringBootApplication
public class MoAiApplication {

	public static void main(String[] args) {
		SpringApplication.run(MoAiApplication.class, args);
	}

}
