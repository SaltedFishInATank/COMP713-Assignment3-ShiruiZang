package nz.ac.aut.comp713.api;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/** Payara supplies the Jakarta REST runtime; this application has no standalone main method. */
@ApplicationPath("/api")
public class RestApplication extends Application { }
