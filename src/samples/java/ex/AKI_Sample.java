package ex;

import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;

public class AKI_Sample extends AllDirectives {

    public void testExtraneousRoute() {
        Route r = route(
                path(PathMatchers.segment("upload").slash(PathMatchers.segment()), (token) -> route(upload(token))));
    }

    public void testExtraneousConcat() {
        Route r = concat(path(PathMatchers.segment("upload").slash(PathMatchers.segment()), (token) -> upload(token)));
    }

    private Route upload(String token) {
        return null;
    }
}
