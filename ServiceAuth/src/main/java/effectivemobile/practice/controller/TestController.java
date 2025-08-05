package effectivemobile.practice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/test")
public class TestController {

    @GetMapping
    public ResponseEntity<Map<String, Integer>> testNode(){
        HashMap<String, Integer> data = new HashMap<>();
        data.put("byte",8);
        data.put("short",16);
        data.put("int",32);
        data.put("long",64);
        data.put("float",32);
        data.put("double",64);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(data);
    }
}
