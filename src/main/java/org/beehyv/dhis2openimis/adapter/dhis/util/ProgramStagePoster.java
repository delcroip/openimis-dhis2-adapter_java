package org.beehyv.dhis2openimis.adapter.dhis.util;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.beehyv.dhis2openimis.adapter.dhis.pojo.post_response.TrackedEntityPostResponse;
import org.beehyv.dhis2openimis.adapter.dhis.pojo.poster.DetailsJson;
import org.beehyv.dhis2openimis.adapter.dhis.pojo.poster.event.Event;
import org.beehyv.dhis2openimis.adapter.dhis.pojo.poster.event.EventBundleRequestBody;
import org.beehyv.dhis2openimis.adapter.dhis.pojo.tracked_entity.query.event.EventQueryDetail;
import org.beehyv.dhis2openimis.adapter.dhis.pojo.tracked_entity.query.event.EventQueryResponse;
import org.beehyv.dhis2openimis.adapter.util.APIConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * @author Shubham Jaiswal
 * Contains methods related to program stage OR event creation and deletion. 
 * Event and ProgramStage are interchangeable words.
 */
@Component
public class ProgramStagePoster {
	private static final Logger logger = LoggerFactory.getLogger(ProgramStagePoster.class);
	private RestTemplate restTemplate;
	private HttpHeaders authHeader;
	
	@Autowired
	public ProgramStagePoster(RestTemplate restTemplate,@Qualifier("Dhis2") HttpHeaders authHeader) {
		this.restTemplate = restTemplate;
		this.authHeader = authHeader;
	}
	
	
	/**
	 * Creates an event and posts program stage data to it.
	 */
	public void postProgramStageData(Event event, DetailsJson data) {
		//Build a proper json needed for adding an event.
		EventBundleRequestBody addEventRequestBody = buildAddEventRequestBody(event);
		
		//Make an API call to add event using the json built above.
		TrackedEntityPostResponse addEventResponse = addEvent(addEventRequestBody);
		
		//Get the event id of the event just created in the above sentence.
		String eventId = getEventId(addEventResponse);
		
		//Make an API call to post actual data to the event id obtained in the above sentence.
		postDataToEvent(data, eventId);
	}
	
	
	private EventBundleRequestBody buildAddEventRequestBody(Event event) {
		List<Event> events = Collections.singletonList(event);
		EventBundleRequestBody addEventRequestBody = new EventBundleRequestBody();
		addEventRequestBody.setEvents(events);
		return addEventRequestBody;
	}
	
	private TrackedEntityPostResponse addEvent(EventBundleRequestBody addEventRequestBody) {
		HttpEntity<EventBundleRequestBody> request = new HttpEntity<>(addEventRequestBody, authHeader);
		
		ResponseEntity<TrackedEntityPostResponse> response = restTemplate.exchange(
									APIConfiguration.DHIS_EVENTS_POST_URL, HttpMethod.POST, request, 
									TrackedEntityPostResponse.class);
		return response.getBody();
	}
	
	private String getEventId(TrackedEntityPostResponse claimDetailEventResponse) {
		String eventId = claimDetailEventResponse.getResponse().getImportSummaries().get(0).getReference();
		return eventId;
	}
	
	private void postDataToEvent(DetailsJson json, String eventId) {
		String url = APIConfiguration.getEventDetailsPostUrl(eventId);
		HttpEntity<DetailsJson> request = new HttpEntity<>(json, authHeader);
		logger.debug("\nPreparing to PUT event data: " + json.toString() + " to url: " + url);
		restTemplate.put(url, request);
	}
	
	
	/**
	 * Deletes all the events associated with the given trackedEntityInstanceId
	 * @param trackedEntityInstanceId
	 */
	public void deleteAllEvents(String trackedEntityInstanceId) {
		//Make API call to get events for given trackedEntityInstanceId
		EventQueryResponse response = makeApiQueryForEvents(trackedEntityInstanceId);
		
		//Use the above response and extract a List of event ids from it.
		List<String> eventIds = extractEventIds(response);
		
		//Delete the above events
		for(String eventId : eventIds) {
			deleteEventId(eventId);
		}
	}
	
	private EventQueryResponse makeApiQueryForEvents(String trackedEntityInstanceId){
		String getEventsUrl = APIConfiguration.getEventsForTrackedEntityInstanceQueryUrl(trackedEntityInstanceId);
		HttpEntity<Void> request = new HttpEntity<Void>(authHeader);
		
		ResponseEntity<EventQueryResponse> response = restTemplate.exchange(
												getEventsUrl, HttpMethod.GET, request, EventQueryResponse.class);
		return response.getBody();
		
	}
	
	private List<String> extractEventIds(EventQueryResponse response){
		List<EventQueryDetail> eventsDetail= response.getEvents();
		
		List<String> output = eventsDetail.stream()
								.map(x -> x.getEvent())
								.collect(Collectors.toList());
		
		return output;
	}
	
	//TODO get the API response to verify if deletion was success.
	private void deleteEventId(String eventId) {
		String url = APIConfiguration.getEventDeleteUrl(eventId);
		HttpEntity<Void> request = new HttpEntity<Void>(authHeader);
		
		restTemplate.exchange(url, HttpMethod.DELETE, request, Object.class);
	}
}
