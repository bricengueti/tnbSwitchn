package TNB.Switch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Sur la classe principale, ex. TnbSwitchApplication
@SpringBootApplication
@EnableScheduling
public class SwitchApplication {

	public static void main(String[] args) {
		SpringApplication.run(SwitchApplication.class, args);
	}

}
