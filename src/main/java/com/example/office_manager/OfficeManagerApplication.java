package com.example.office_manager;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "Workly Office Manager API",
        version = "v1",
        description = "사원·연차·전자결재·근태·협업 기능을 제공하는 업무관리 REST API"
))
public class OfficeManagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(OfficeManagerApplication.class, args);
	}

}
