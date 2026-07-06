package co.id.mcs.dika.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class AgentService {

    private final RestTemplate restTemplate;

    @Value("${agent.api.url}")
    private String agentApiUrl;

    public AgentService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Call external agent API to get agent full name by user id
     */
    @SuppressWarnings("unchecked")
    public String getAgentFullName(String userId) {
        Map<String, Object> agent = getAgentByUserId(userId);
        return agent != null ? (String) agent.get("full_name") : null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getAgentByUserId(String userId) {
        return getAgentByField("agent_id", userId);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getAgentByUsername(String username) {
        return getAgentByField("username", username);
    }

    private Map<String, Object> getAgentByField(String field, String value) {
        try {
            String url = agentApiUrl + "/api/agent/list";

            // Prepare request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("additionalInfo", new HashMap<>());

            Map<String, Object> payload = new HashMap<>();
            payload.put("select", new ArrayList<>());
            payload.put("search", "");

            // Use provided where clause
            Map<String, Object> where = new HashMap<>();
            where.put(field, value);
            payload.put("where", where);

            payload.put("page", 1);
            payload.put("limit", 1);
            requestBody.put("payload", payload);

            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Call API
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class);

            // Parse response
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Map<String, Object> payloadMap = (Map<String, Object>) responseBody.get("payload");

                if (payloadMap != null) {
                    List<Map<String, Object>> agents = (List<Map<String, Object>>) payloadMap.get("payload");

                    if (agents != null && !agents.isEmpty()) {
                        return agents.get(0);
                    }
                }
            }

            return null;
        } catch (Exception e) {
            log.error("Error calling agent API with {}: {}", field, value, e);
            return null;
        }
    }

    /**
     * Call external agent API to get hierarchy
     */
    /**
     * Call external agent API to get hierarchy
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getHierarchy(Object requestBody) {
        try {
            String url = agentApiUrl + "/api/agent/hierarchy";

            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Object> entity = new HttpEntity<>(requestBody, headers);

            // Call API
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class);

            // Parse response
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Map<String, Object> payloadMap = (Map<String, Object>) responseBody.get("payload");

                if (payloadMap != null) {
                    return payloadMap;
                }
            }

            return new HashMap<>();
        } catch (Exception e) {
            log.error("Error calling agent hierarchy API", e);
            return new HashMap<>();
        }
    }

    /**
     * Call external agent API to get list of agents
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getAgentList(Map<String, Object> requestBody) {
        try {
            String url = agentApiUrl + "/api/agent/list";

            if (requestBody != null) {
                requestBody.remove("appReferenceId");
            }

            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Call API
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class);

            // Parse response
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }

            return new HashMap<>();
        } catch (Exception e) {
            log.error("Error calling agent list API", e);
            return new HashMap<>();
        }
    }

    /**
     * Call external agent API to get count of agents by supervisor
     */
    @SuppressWarnings("unchecked")
    public Long getCountBySupervisor(UUID supervisor) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("additionalInfo", new HashMap<>());

            Map<String, Object> payload = new HashMap<>();
            Map<String, Object> where = new HashMap<>();
            where.put("supervisor", supervisor);
            payload.put("where", where);
            payload.put("page", 1);
            payload.put("limit", 100);
            requestBody.put("payload", payload);

            Map<String, Object> response = getAgentList(requestBody);
            if (response != null && response.containsKey("payload")) {
                Map<String, Object> pMap = (Map<String, Object>) response.get("payload");
                if (pMap.containsKey("total")) {
                    Object totalObj = pMap.get("total");
                    if (totalObj instanceof Integer) {
                        return ((Integer) totalObj).longValue();
                    } else if (totalObj instanceof Long) {
                        return (Long) totalObj;
                    } else if (totalObj != null) {
                        return Long.valueOf(totalObj.toString());
                    }
                }
            }
            return 0L;
        } catch (Exception e) {
            log.error("Error calling agent list API for supervisor count: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * Update agent storage name in external service
     */
    public void updateAgentStorageName(String agentId, Object storageName) {
        try {
            Map<String, Object> agent = getAgentByUserId(agentId);
            if (agent != null) {
                agent.put("storage_name", storageName);
                saveAgent(agent);
            } else {
                log.warn("Could not find agent to update storage_name for id: {}", agentId);
            }
        } catch (Exception e) {
            log.error("Error updating agent storage name for id: {}", agentId, e);
        }
    }

    /**
     * Call external agent API to save/update agent
     */
    public void saveAgent(Map<String, Object> agentData) {
        try {
            String url = agentApiUrl + "/api/agent/save";

            // Prepare request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("additionalInfo", new HashMap<>());
            requestBody.put("payload", agentData);

            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Call API
            restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            log.info("Successfully updated agent in external service: {}", agentData.get("username"));

        } catch (Exception e) {
            log.error("Error calling agent save API: {}", e.getMessage());
        }
    }
}
