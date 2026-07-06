package co.id.mcs.dika;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import co.id.mcs.ptdika.MadMachine.Repository.EnableRepositoryJdbc;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.apache.poi.util.IOUtils;

@SpringBootApplication
@EnableRepositoryJdbc
@EnableScheduling
@EnableAsync
public class SchedjulerApplication {

	@PostConstruct
	public void init() {
		// Set global JVM timezone to Asia/Jakarta so new Date() automatically uses WIB
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Jakarta"));

		// Increase POI allocation limit for large files (default is 100MB)
		// Set to 2GB to accommodate the requested ~1.3GB record
		IOUtils.setByteArrayMaxOverride(2_000_000_000);
	}

	public static void main(String[] args) {
		SpringApplication.run(SchedjulerApplication.class, args);
	}

}
