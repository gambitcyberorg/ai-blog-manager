package org.gc.aiagents.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

@FeignClient(name = "firecrawl-integration", url = "https://api.firecrawl.dev/v0")
public interface FireCrawlClient {

    @RequestMapping(method = RequestMethod.POST, value = "/scrape")
    Map<String, Object> scrapeData(@RequestHeader(value = "Authorization", required = true) String authorizationHeader, @RequestBody Map<String, Object> data);
}
