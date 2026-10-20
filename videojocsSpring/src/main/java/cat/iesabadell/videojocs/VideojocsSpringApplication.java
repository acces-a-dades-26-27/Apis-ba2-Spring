package cat.iesabadell.videojocs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class VideojocsSpringApplication {

	public static void main(String[] args) {
		SpringApplication.run(VideojocsSpringApplication.class, args);
		
		// http://localhost:8080/swagger-ui/index.html#/ per obrir a Swagger
	}

}
