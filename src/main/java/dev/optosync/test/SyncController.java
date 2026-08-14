package dev.optosync.test;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
    @PostMapping(path = "/{lane}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> sync(
            @PathVariable String lane,
            @RequestBody Map<String, Object> mutation) {
        var response = new LinkedHashMap<String, Object>();
        response.put("lane", lane);
        response.put("acknowledgedThrough", mutation.get("sequence"));
        response.put("authoritative", mutation.get("json"));
        return response;
    }
}
