package com.ac.jira.cloud;

import com.ac.jira.cloud.rest.CloudListener;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.mock.component.MockComponentWorker;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.util.EmailFormatter;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static javax.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.glassfish.jersey.test.TestProperties.DUMP_ENTITY;
import static org.glassfish.jersey.test.TestProperties.LOG_TRAFFIC;
import static org.mockito.Mockito.mock;


public class CloudListenerTest extends JerseyTest {
    @Override
    protected Application configure() {
        enable(LOG_TRAFFIC);
        enable(DUMP_ENTITY);
        return new ResourceConfig(CloudListener.class);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        JiraAuthenticationContext jiraAuthenticationContext = mock(JiraAuthenticationContext.class);
        EmailFormatter formatter = mock(EmailFormatter.class);
        ProjectManager projectManager = mock(ProjectManager.class);
        UserManager userManager = mock(UserManager.class);
        IssueManager issueManager = mock(IssueManager.class);
        new MockComponentWorker()
                .addMock(JiraAuthenticationContext.class, jiraAuthenticationContext)
                .addMock(EmailFormatter.class, formatter)
                .addMock(ProjectManager.class, projectManager)
                .addMock(UserManager.class, userManager)
                .addMock(IssueManager.class, issueManager)
                .init();
    }

    @Test
    public void shouldReturnOKWhenVersionCreatedJsonComesToVersionEventListener() throws Exception {
        String request = readFromResource("events/version_created.json");

        Response response = target("/project/IP/version/123")
                .request()
                .post(Entity.json(request));

        assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
    }

    @Test
    public void shouldReturnOKWhenVersionUpdatedJsonComesToVersionEventListener() throws Exception {
        String request = readFromResource("events/version_updated.json");

        Response response = target("/project/IP/version/123")
                .request()
                .post(Entity.json(request));

        assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
    }

    @Test
    public void shouldReturnOKWhenVersionDeletedJsonComesToVersionEventListener() throws Exception {
        String request = readFromResource("events/version_deleted.json");

        Response response = target("/project/IP/version/123")
                .request()
                .post(Entity.json(request));

        assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
    }

    @Test
    public void shouldReturnOKWhenCommentCreatedJsonComesToCommentCreatedEventListener() throws Exception {
        String request = readFromResource("events/comment_created.json");

        Response response = target("/project/IP/issue/IP-1/comment/1/create")
                .request()
                .post(Entity.json(request));

        assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
    }

    @Test
    public void shouldReturnOKWhenCommentUpdatedJsonComesToCommentUpdatedEventListener() throws Exception {
        String request = readFromResource("events/comment_updated.json");

        Response response = target("/project/IP/issue/IP-1/comment/1/update")
                .request()
                .post(Entity.json(request));

        assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
    }

    @Test
    public void shouldReturnOKWhenCommentDeletedJsonComesToCommentDeletedEventListener() throws Exception {
        String request = readFromResource("events/comment_deleted.json");

        Response response = target("/project/IP/issue/IP-1/comment/1/delete")
                .request()
                .post(Entity.json(request));

        assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
    }

    @Test
    public void shouldReturnOKWhenWorklogCreatedJsonComesToWorklogCreatedEventListener() throws Exception {
        String request = readFromResource("events/worklog_created.json");

        Response response = target("/project/IP/worklog/create")
                .request()
                .post(Entity.json(request));

        assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
    }

    @Test
    public void shouldReturnOKWhenWorklogUpdatedJsonComesToWorklogUpdatedEventListener() throws Exception {
        String request = readFromResource("events/worklog_updated.json");

        Response response = target("/project/IP/worklog/update")
                .request()
                .post(Entity.json(request));

        assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
    }

    @Test
    public void shouldReturnOKWhenWorklogDeletedJsonComesToWorklogDeletedEventListener() throws Exception {
        String request = readFromResource("events/worklog_deleted.json");

        Response response = target("/project/IP/worklog/delete")
                .request()
                .post(Entity.json(request));

        assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
    }

    @Test
    @Ignore
    public void shouldReturnOKWhenIssueCreatedJsonComesToIssueCreatedEventListener() throws Exception {
        String request = readFromResource("events/issue_created.json");

        Response response = target("/project/IP/issue/IP-1/create")
                .request()
                .post(Entity.json(request));

        assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
    }

    @Test
    public void shouldReturnOKWhenIssueUpdatedJsonComesToIssueUpdatedEventListener() throws Exception {
        String request = readFromResource("events/issue_updated.json");

        Response response = target("/project/IP/issue/IP-1/update")
                .request()
                .post(Entity.json(request));

        assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
    }


    private String readFromResource(String resource) throws URISyntaxException, IOException {
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        URL url = classloader.getResource(resource);
        assert url != null;
        Path file = Paths.get(url.toURI());
        return new String(Files.readAllBytes(file));
    }
}