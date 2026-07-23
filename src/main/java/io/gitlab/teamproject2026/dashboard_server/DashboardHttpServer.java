package io.gitlab.teamproject2026.dashboard_server;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/** Minimal HTTP server that is accessed by the dashboard frontend */
public class DashboardHttpServer {
    private final HttpServer server;

    public DashboardHttpServer(int port, DashboardQueriesHandler dashboardQueriesHandler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/stats", dashboardQueriesHandler);
        server.setExecutor(Executors.newFixedThreadPool(4));
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(1);
    }
}
