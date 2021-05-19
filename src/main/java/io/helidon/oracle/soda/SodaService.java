
package io.helidon.oracle.soda;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonException;
import javax.json.JsonObject;

import org.json.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import oracle.soda.OracleCollection;
import oracle.soda.OracleCursor;
import oracle.soda.OracleDatabase;
import oracle.soda.OracleDocument;
import oracle.soda.OracleException;

/**
 * A simple service to greet you. Examples:
 *
 * Get default greeting message: curl -X GET http://localhost:8080/greet
 *
 * Get greeting message for Joe: curl -X GET http://localhost:8080/greet/Joe
 *
 * Change greeting curl -X PUT -H "Content-Type: application/json" -d
 * '{"greeting" : "Howdy"}' http://localhost:8080/greet/greeting
 *
 * The message is returned as a JSON object
 */

public class SodaService implements Service {

    /**
     * The config value for the key {@code greeting}.
     */
    private final AtomicReference<String> greeting = new AtomicReference<>();

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private static final Logger LOGGER = Logger.getLogger(SodaService.class.getName());

    ProducerService sodaproducer = new ProducerService();
    public OracleDatabase db = sodaproducer.dbConnect();

    SodaService(Config config) {
        greeting.set(config.get("app.greeting").asString().orElse("Ciao"));
    }

    /**
     * A service registers itself by updating the routing rules.
     * 
     * @param rules the routing rules.
     */
    @Override
    public void update(Routing.Rules rules) {
        rules.get("/", this::getDefaultMessageHandler)
        .get("/status", this::getStatus)
        .post("/login", this::loginUser)
        .get("/catalog", this::getCatalog);
    }

    /**
     * Return a worldly greeting message.
     * 
     * @param request  the server request
     * @param response the server response
     */
    private void getDefaultMessageHandler(ServerRequest request, ServerResponse response) {
        sendResponse(response, "eShop for oracle soda!!");
    }

    /**
     * Return a worldly greeting message.
     * 
     * @param request  the server request
     * @param response the server response
     */
    private void getStatus(ServerRequest request, ServerResponse response) {
        sendResponse(response, "healthy");
    }

    /**
     * Return a worldly greeting message.
     * 
     * @param request  the server request
     * @param response the server response
     */
    private void loginUser(ServerRequest request, ServerResponse response) {
        System.out.println("==================================");
        System.out.println("Calling REST Service: /login   \n");
        request.content().as(JsonObject.class).thenAccept(jo -> checkUserExists(jo, response))
                .exceptionally(ex -> processErrors(ex, request, response));

    }

    private void checkUserExists(JsonObject jo, ServerResponse response) {

        if (!jo.containsKey("username") && !jo.containsKey("password")) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "Credentials not passed correctly")
                    .build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        }
//{"$and" : ["username":"<username>","password":"<assword>"]}
        String _valueFilter = "{\"$and\" : [ {\"username\" : \"" + jo.getString("username") + "\"}, {\"password\" : \""
                + jo.getString("password") + "\"} ]}";
        // String _valueFilter = "{\"$and\" : [ {\"username\" : \"\"}, {\"password\" :
        // \""+jo.getString("password")+"\"} ]}";

        try {
            OracleDocument filterSpec = db.createDocumentFromString(_valueFilter);

            OracleCollection col = this.db.openCollection("eshop_users");
            OracleCursor c = col.find().filter(filterSpec).getCursor();

            if (c.hasNext()) {

                OracleDocument resultDoc;

                while (c.hasNext()) {
                    // Get the next document.
                    resultDoc = c.next();

                    // Print document components
                    System.out.println("Key:         " + resultDoc.getKey());
                    System.out.println("Content:     " + resultDoc.getContentAsString());
                    System.out.println("Version:     " + resultDoc.getVersion());
                    System.out.println("Last modified: " + resultDoc.getLastModified());
                    System.out.println("Created on:    " + resultDoc.getCreatedOn());
                    System.out.println("Media:         " + resultDoc.getMediaType());
                    System.out.println("\n");
                }

                System.out.println("\n successful response: true");
                System.out.println("================================== \n");
                sendResponse(response, true);

            } else {
                System.out.println("\n error response: false");
                System.out.println("================================== \n");
                sendResponse(response, false);
            }

        } catch (OracleException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Return a greeting message using the name that was provided.
     * 
     * @param request  the server request
     * @param response the server response
     */
    private void getCatalog(ServerRequest request, ServerResponse response) {
        System.out.println("==================================");
        System.out.println("Calling REST Service: /catalog   \n");
        OracleCollection col;
        try {
            col = this.db.openCollection("eshop_items");
            OracleCursor c = col.find().getCursor();
            OracleDocument resultDoc;
            JSONArray jsonArray = new JSONArray();
            while (c.hasNext()) {

                resultDoc = c.next();
                System.out.println(resultDoc.getContentAsString()); 
                
                JSONParser parser = new JSONParser();
				Object obj = parser.parse(resultDoc.getContentAsString());
                jsonArray.put(obj);
            }
            

            System.out.println("\n successful response: true");
            System.out.println("================================== \n");
            sendResponse(response, jsonArray);
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }catch (OracleException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    private void sendResponse(ServerResponse response, Object name) {
        String msg = String.format("%s", name);

        JsonObject returnObject = JSON.createObjectBuilder().add("message", msg).build();
        response.send(returnObject);
    }

    private static <T> T processErrors(Throwable ex, ServerRequest request, ServerResponse response) {

        if (ex.getCause() instanceof JsonException) {

            LOGGER.log(Level.FINE, "Invalid JSON", ex);
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "Invalid JSON").build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
        } else {

            LOGGER.log(Level.FINE, "Internal error", ex);
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "Internal error").build();
            response.status(Http.Status.INTERNAL_SERVER_ERROR_500).send(jsonErrorObject);
        }

        return null;
    }

    private void updateItemFromJson(JsonObject jo, ServerResponse response) {
        if (!jo.containsKey("item")) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "No greeting provided").build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        }

        greeting.set(jo.getString("item"));
        response.status(Http.Status.NO_CONTENT_204).send();
    }

    /**
     * Set the greeting to use in future messages.
     * 
     * @param request  the server request
     * @param response the server response
     */
    private void updateItemHandler(ServerRequest request, ServerResponse response) {
        request.content().as(JsonObject.class).thenAccept(jo -> updateItemFromJson(jo, response))
                .exceptionally(ex -> processErrors(ex, request, response));
    }
}