package org.beehyv.dhis2openimis.adapter.dhis.claim;

import java.time.LocalDate;

import org.beehyv.dhis2openimis.adapter.dhis.cache.program.ProgramCache;
import org.beehyv.dhis2openimis.adapter.dhis.pojo.post_response.TrackedEntityPostResponse;
import org.beehyv.dhis2openimis.adapter.dhis.pojo.poster.TrackedEntityRequest;
import org.beehyv.dhis2openimis.adapter.dhis.pojo.poster.enrollment.EnrollmentRequestBody;
import org.beehyv.dhis2openimis.adapter.dhis.pojo.program.Program;
import org.beehyv.dhis2openimis.adapter.dhis.util.CreateEventDataPojo;
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

@Component
public class ClaimPoster {
	
	private static final Logger logger = LoggerFactory.getLogger(ClaimPoster.class);
	
	private RestTemplate restTemplate;
	private HttpHeaders authHeaders;
	private ProgramCache programCache;
	
	@Autowired
	public ClaimPoster(@Qualifier("Dhis2")HttpHeaders authHeaders, ProgramCache programCache, RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
		this.authHeaders = authHeaders;
		this.programCache = programCache;
	}
	
	private Program getProgramDetails() {
		return programCache.getByDisplayName("Claims management");
	}
	
	public CreateEventDataPojo postAndGetClaimManagementEnrollmentId(TrackedEntityRequest claim, String createdDate) {
		TrackedEntityPostResponse claimResponse = post(claim);
		logger.debug(claim.toString());
		logger.debug("Claim posted!");
		
		String orgUnit = claim.getOrgUnit();
		String trackedEntityId = getReferenceId(claimResponse);
		EnrollmentRequestBody enrollment = buildEnrollmentBody(trackedEntityId, orgUnit, createdDate);
		TrackedEntityPostResponse enrollmentResponse = post(enrollment);
		logger.debug("Claim Enrollment posted!");
		
		String enrollmentId = getReferenceId(enrollmentResponse);
		
		CreateEventDataPojo output = new CreateEventDataPojo(trackedEntityId, orgUnit, enrollmentId);
		return output;
	}
	
	
	public void updateClaim(TrackedEntityRequest claim, String claimTrackedEntityId) {
		String url = getClaimUpdateUrl(claimTrackedEntityId);
		HttpEntity<TrackedEntityRequest> request = new HttpEntity<>(claim, authHeaders);
		logger.debug("\nPreparing to PUT claim: " + claim.toString() + " to url: " + url);
		restTemplate.put(url, request);
	}
	
	private String getClaimUpdateUrl(String claimTrackedEntityId) {
    	StringBuilder url = new StringBuilder(APIConfiguration.DHIS_TRACKED_ENTITY_INSTANCES_URL);
    	url.append("/" + claimTrackedEntityId);
    	
    	String programId = getProgramDetails().getId();
    	url.append("?program=" + programId);
    	
    	return url.toString();
    }
	
	private String getReferenceId(TrackedEntityPostResponse response) {
		return response.getResponse().getImportSummaries().get(0).getReference();
	}

	private TrackedEntityPostResponse post(TrackedEntityRequest claim) {
		HttpEntity<TrackedEntityRequest> request = new HttpEntity<TrackedEntityRequest>(claim, authHeaders);
		logger.debug("Posting claim: " + claim.toString() + " to url: " + APIConfiguration.DHIS_TRACKED_ENTITY_INSTANCES_URL);
		ResponseEntity<TrackedEntityPostResponse> response = restTemplate.exchange(APIConfiguration.DHIS_TRACKED_ENTITY_INSTANCES_URL, 
				HttpMethod.POST, request, TrackedEntityPostResponse.class);
		
		return response.getBody();
	}
	
	
	private TrackedEntityPostResponse post(EnrollmentRequestBody enrollment) {
		HttpEntity<EnrollmentRequestBody> request = new HttpEntity<>(enrollment, authHeaders);
		
		ResponseEntity<TrackedEntityPostResponse> response = restTemplate.exchange(
				APIConfiguration.DHIS_ENROLLMENT_POST_URL, HttpMethod.POST, request, TrackedEntityPostResponse.class);
		
		return response.getBody();
	}
	
	
	private EnrollmentRequestBody buildEnrollmentBody(String referenceId, String orgUnit, String createdDate) {
		EnrollmentRequestBody enrollment = new EnrollmentRequestBody();
		enrollment.setTrackedEntityInstance(referenceId);
		enrollment.setOrgUnit(orgUnit);
		enrollment.setStatus("ACTIVE");
		
		String programId = getProgramDetails().getId();
		enrollment.setProgram(programId);
		
		enrollment.setEnrollmentDate(createdDate);
		enrollment.setIncidentDate(createdDate);
		return enrollment;
	}
	
	
	
	
	
}
