package co.id.mcs.dika.util;

import co.id.mcs.dika.dto.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Slf4j
public class ResponseUtil {

    public static <T> ResponseEntity<BaseResponse<T>> build(String serviceCode, String actionCode, T payload) {
        BaseResponse<T> response = new BaseResponse<>();
        response.setPayload(payload);
        response.setCode(serviceCode + "-" + HttpStatus.OK.value() + "-" + actionCode);
        response.setMessage("Success");
        
        log.info("Success request data...");

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
