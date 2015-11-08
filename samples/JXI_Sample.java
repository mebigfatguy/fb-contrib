import java.io.InputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

public class JXI_Sample {

    @GET
    @Path("/stuff")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON) 
    public Response loadStuff(Stuff s) {
        return Response.ok().build();
    }
    
    @POST
    @Path("/stuffit/{blub}")
    public Response stufStuff(@PathParam("blub") String blub, Stuff s) {
        return Response.ok().build();
    }
    
    @GET
    @Path("/stuffNotRight/{good}")
    public Response mismatchParm(@PathParam("bad") String bad) {
        return Response.ok().build();
    }
    
    @PUT
    @Path("/stuffMe")
    public Response stuffWithBadContext(@Context InputStream is) {
        return Response.ok().build();
    }
    
    @GET
    @Path("/stuffy")
    @Produces(MediaType.APPLICATION_JSON) 
    public Response fpFine(Stuff s) {
        return Response.ok().build();
    }

    
    @POST
    @Path("/stuffok/{blub}")
    public Response fpStuff(@PathParam("blub") String blub, String body) {
        return Response.ok().build();
    }
    
    @POST
    @Path("/stuffyup/{blub}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response fpStuff2(@PathParam("blub") String blub, Stuff body) {
        return Response.ok().build();
    }
    
    @PUT
    @Path("/stuffGoodContext")
    public Response fpStuffGoodContext(@Context UriInfo info) {
        return Response.ok().build();
    }
    
    static class Stuff {
    }
}
