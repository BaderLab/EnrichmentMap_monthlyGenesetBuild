import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for the GeneSetTools Java fixes (GitHub issues #37-#41).
 *
 * No test framework is needed: run with
 *   java -cp <classes>:<lib/*> GenesetToolsTests
 * and it exits 1 if any check fails.
 *
 * UniProt is replaced by a fake ID mapping server (FakeUniprot, below) on
 * 127.0.0.1, and the command line tests run GenesetTools in a separate JVM
 * so that System.exit() can be checked.
 */
public class GenesetToolsTests {

    static int checks = 0;
    static int failures = 0;
    static String currentTest = "";

    static void check(boolean ok, String message) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL [" + currentTest + "]: " + message);
        }
    }

    static void checkEquals(Object expected, Object actual, String message) {
        check(Objects.equals(expected, actual), message + " (expected <" + expected + ">, got <" + actual + ">)");
    }

    interface TestBody { void run() throws Exception; }

    static final java.util.concurrent.ExecutorService testThreads =
            Executors.newCachedThreadPool(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });

    /** Run one test; a test that hangs (e.g. an endless status loop) fails after 20 s instead of blocking the run. */
    static void test(String name, TestBody body) {
        currentTest = name;
        System.out.println("--- " + name);
        java.util.concurrent.Future<?> f = testThreads.submit(() -> { body.run(); return null; });
        try {
            f.get(20, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            f.cancel(true);
            failures++;
            System.out.println("  FAIL [" + name + "]: did not finish within 20 s (hung)");
        } catch (java.util.concurrent.ExecutionException e) {
            failures++;
            System.out.println("  FAIL [" + name + "]: unexpected exception " + e.getCause());
            e.getCause().printStackTrace(System.out);
        } catch (InterruptedException e) {
            failures++;
            System.out.println("  FAIL [" + name + "]: interrupted");
        }
    }

    static Throwable thrownBy(TestBody body) {
        try {
            body.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    static FakeUniprot server;

    public static void main(String[] args) throws Exception {
        server = new FakeUniprot();
        // QueryUniprotREST reads these when the class is first used, so set them before that
        System.setProperty("uniprot.server", server.baseUrl());
        System.setProperty("uniprot.status.wait.ms", "0");
        System.setProperty("uniprot.status.max.checks", "5");

        batchingTests();
        jobStatusTests();
        webServiceTests();
        commandLineTests();

        server.stop();
        System.out.println();
        if (failures == 0) {
            System.out.println("PASSED: " + checks + " checks");
            System.exit(0);
        }
        System.out.println("FAILED: " + failures + " of " + checks + " checks failed");
        System.exit(1);
    }

    static String[] ids(int n, String prefix) {
        String[] ids = new String[n];
        for (int i = 0; i < n; i++) ids[i] = prefix + i;
        return ids;
    }

    static HashSet<String> idSet(int n, String prefix) {
        return new HashSet<>(Arrays.asList(ids(n, prefix)));
    }

    // ------------------------------------------------------------------ #38
    static void batchingTests() {
        test("splitIntoBatches puts every id in exactly one batch, in order (#38)", () -> {
            for (int n : new int[]{0, 1, 999, 1000, 1001, 1999, 2000, 2001, 2500, 3000, 10001}) {
                String[] input = ids(n, "ID");
                List<String[]> batches = QueryUniprotREST.splitIntoBatches(input, 1000);
                List<String> joined = new ArrayList<>();
                for (String[] b : batches) {
                    check(b.length >= 1 && b.length <= 1000, "n=" + n + ": batch size " + b.length + " not in 1..1000");
                    joined.addAll(Arrays.asList(b));
                }
                checkEquals(Arrays.asList(input), joined, "n=" + n + ": all ids, once each, in order");
                checkEquals((int) Math.ceil(n / 1000.0), batches.size(), "n=" + n + ": number of batches");
            }
            // the ids the old loop dropped for 2,500 ids
            List<String> joined = new ArrayList<>();
            for (String[] b : QueryUniprotREST.splitIntoBatches(ids(2500, "ID"), 1000)) joined.addAll(Arrays.asList(b));
            check(joined.contains("ID1000") && joined.contains("ID2000") && joined.contains("ID2499"),
                    "ids 1000, 2000 and 2499 (skipped by the old loop) are sent");
        });

        test("splitIntoBatches with other batch sizes (#38)", () -> {
            List<String[]> b = QueryUniprotREST.splitIntoBatches(ids(10, "x"), 3);
            checkEquals(4, b.size(), "10 ids in batches of 3");
            checkEquals(Arrays.asList("x9"), Arrays.asList(b.get(3)), "last batch has the last id");
            check(thrownBy(() -> QueryUniprotREST.splitIntoBatches(ids(10, "x"), 0)) instanceof IllegalArgumentException,
                    "batch size 0 is rejected");
        });
    }

    // ------------------------------------------------------------------ #37
    static JSONObject status(String s) {
        JSONObject o = new JSONObject();
        o.put("jobStatus", s);
        return o;
    }

    static void jobStatusTests() {
        test("a job that stays RUNNING is given up on after maxChecks checks (#37)", () -> {
            AtomicInteger calls = new AtomicInteger();
            boolean finished = QueryUniprotREST.waitForJob("job", id -> { calls.incrementAndGet(); return status("RUNNING"); }, 7, 0);
            check(!finished, "returns false");
            checkEquals(7, calls.get(), "number of status checks");
        });

        test("unexpected statuses also count towards the limit (#37)", () -> {
            AtomicInteger calls = new AtomicInteger();
            boolean finished = QueryUniprotREST.waitForJob("job", id -> { calls.incrementAndGet(); return status("WEIRD"); }, 4, 0);
            check(!finished, "returns false");
            checkEquals(4, calls.get(), "number of status checks");
            calls.set(0);
            finished = QueryUniprotREST.waitForJob("job", id -> { calls.incrementAndGet(); return new JSONObject(); }, 4, 0);
            check(!finished, "an empty response returns false");
            checkEquals(4, calls.get(), "number of status checks for empty responses");
        });

        test("waits between checks instead of polling in a tight loop (#37)", () -> {
            for (String s : new String[]{"RUNNING", "NEW", "SOMETHING_ELSE"}) {
                long start = System.nanoTime();
                QueryUniprotREST.waitForJob("job", id -> status(s), 3, 40);
                long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                check(ms >= 80, s + ": 2 waits of 40 ms between 3 checks (took " + ms + " ms)");
            }
        });

        test("NEW/RUNNING then FINISHED returns true; ERROR returns false (#37)", () -> {
            Iterator<String> seq = Arrays.asList("NEW", "RUNNING", "FINISHED").iterator();
            AtomicInteger calls = new AtomicInteger();
            check(QueryUniprotREST.waitForJob("job", id -> { calls.incrementAndGet(); return status(seq.next()); }, 10, 0),
                    "finished job returns true");
            checkEquals(3, calls.get(), "stops checking once finished");
            calls.set(0);
            check(!QueryUniprotREST.waitForJob("job", id -> { calls.incrementAndGet(); return status("ERROR"); }, 10, 0),
                    "error returns false");
            checkEquals(1, calls.get(), "stops checking after an error");
            JSONObject results = new JSONObject();
            results.put("results", new JSONArray());
            check(QueryUniprotREST.waitForJob("job", id -> results, 10, 0),
                    "a response that already has results (no jobStatus) counts as finished");
        });

        test("connection and request timeouts are set (#37)", () -> {
            checkEquals(30L, QueryUniprotREST.CONNECT_TIMEOUT.getSeconds(), "connect timeout seconds");
            checkEquals(300L, QueryUniprotREST.REQUEST_TIMEOUT.getSeconds(), "default request timeout seconds");
        });
    }

    // ------------------------------------------------------------ #37-#41
    static void webServiceTests() {
        test("2,500 ids: 3 jobs, every id sent once, every result collected (#38)", () -> {
            server.reset();
            HashSet<String> input = idSet(2500, "");
            QueryUniprotREST q = new QueryUniprotREST("entrezgene", "uniprot", input, 9606);
            String[] jobs = q.submitJob();
            checkEquals(3, jobs.length, "number of jobs");
            check(!Arrays.asList(jobs).contains(null), "no null job ids");
            List<Integer> sizes = new ArrayList<>();
            List<String> sent = new ArrayList<>();
            for (FakeUniprot.Run r : server.runs) { sizes.add(r.ids.size()); sent.addAll(r.ids); }
            checkEquals(Arrays.asList(1000, 1000, 500), sizes, "ids per job");
            checkEquals(2500, sent.size(), "ids sent in total");
            checkEquals(input, new HashSet<>(sent), "every id sent");
            HashMap<String, Set<String>> result = q.getJobResults(jobs);
            checkEquals(2500, result.size(), "translations returned (results are paged, 400 per page)");
            checkEquals(Collections.singleton("MAPPED_1234"), result.get("1234"), "translation of id 1234");
            check(!server.statusChecks.containsKey("null"), "job 'null' is never queried");
        });

        test("exactly 2,000 ids: 2 jobs and no 'null' job (#38)", () -> {
            server.reset();
            QueryUniprotREST q = new QueryUniprotREST("entrezgene", "uniprot", idSet(2000, ""), 9606);
            String[] jobs = q.submitJob();
            checkEquals(2, jobs.length, "number of jobs");
            checkEquals(2000, q.getJobResults(jobs).size(), "translations returned");
            check(!server.statusChecks.containsKey("null"), "job 'null' is never queried");
        });

        test("taxId is sent for symbol and entrez gene lookups, not for UniProt ids (#41)", () -> {
            server.reset();
            new QueryUniprotREST("entrezgene", "uniprot", idSet(3, ""), 10090).submitJob();
            new QueryUniprotREST("symbol", "uniprot", new HashSet<>(Arrays.asList("TP53", "BRCA1")), 9606).submitJob();
            new QueryUniprotREST("uniprot", "symbol", new HashSet<>(Arrays.asList("P04637")), 9606).submitJob();
            checkEquals(3, server.runs.size(), "jobs submitted");
            checkEquals("GeneID", server.runs.get(0).from, "entrezgene lookup 'from'");
            checkEquals("10090", server.runs.get(0).taxId, "entrezgene lookup taxId");
            checkEquals("Gene_Name", server.runs.get(1).from, "symbol lookup 'from'");
            checkEquals("9606", server.runs.get(1).taxId, "symbol lookup taxId");
            checkEquals("UniProtKB_AC-ID", server.runs.get(2).from, "UniProt lookup 'from'");
            checkEquals(null, server.runs.get(2).taxId, "no taxId for UniProt ids");
            check(QueryUniprotREST.usesTaxIdFilter("GeneID") && QueryUniprotREST.usesTaxIdFilter("Gene_Name")
                    && !QueryUniprotREST.usesTaxIdFilter("UniProtKB_AC-ID") && !QueryUniprotREST.usesTaxIdFilter("Ensembl"),
                    "usesTaxIdFilter");
        });

        test("a job that never finishes is an error, not a partial result (#37, #39)", () -> {
            server.reset();
            server.statusMode = "running";
            QueryUniprotREST q = new QueryUniprotREST("uniprot", "symbol", idSet(10, "P"), 9606);
            String[] jobs = q.submitJob();
            Throwable t = thrownBy(() -> q.getJobResults(jobs));
            check(t != null && String.valueOf(t.getMessage()).contains("Unable to get Finished status"),
                    "getJobResults throws (got " + t + ")");
            checkEquals(5, server.statusChecks.get(jobs[0]).get(), "status checked uniprot.status.max.checks (5) times");
        });

        test("a job that reports ERROR is an error (#39)", () -> {
            server.reset();
            server.statusMode = "error";
            QueryUniprotREST q = new QueryUniprotREST("symbol", "uniprot", idSet(10, "G"), 9606);
            String[] jobs = q.submitJob();
            check(thrownBy(() -> q.getJobResults(jobs)) != null, "getJobResults throws");
            checkEquals(1, server.statusChecks.get(jobs[0]).get(), "status checked once");
        });

        test("no job id from uniprot is an error (#39)", () -> {
            server.reset();
            server.runMode = "noJobId";
            Throwable t = thrownBy(() -> new QueryUniprotREST("uniprot", "symbol", idSet(10, "P"), 9606).submitJob());
            check(t != null && String.valueOf(t.getMessage()).contains("did not return a job id"),
                    "submitJob throws (got " + t + ")");
        });

        test("a lookup with no UniProt id type is rejected (#39)", () -> {
            server.reset();
            Throwable t = thrownBy(() -> new QueryUniprotREST("entrezgene", "symbol", idSet(10, ""), 9606).submitJob());
            check(t instanceof IllegalArgumentException, "IllegalArgumentException (got " + t + ")");
            checkEquals(0, server.runs.size(), "nothing sent to uniprot");
        });
    }

    // ------------------------------------------------------------ #39 #40
    static class Result {
        int exit;
        String out;
        long ms;
        boolean timedOut;
    }

    static Result runTool(File dir, List<String> jvmProps, String... args) throws Exception {
        return runTool(60, dir, jvmProps, args);
    }

    static Result runTool(int timeoutSeconds, File dir, List<String> jvmProps, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        cmd.addAll(jvmProps);
        cmd.add("-Duser.home=" + dir.getAbsolutePath()); // cytoscape writes its config to ~/.cytoscape
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("GenesetTools");
        cmd.addAll(Arrays.asList(args));
        File outFile = File.createTempFile("tool_output", ".txt", dir);
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir).redirectErrorStream(true)
                .redirectOutput(outFile);
        long start = System.nanoTime();
        Process p = pb.start();
        Result r = new Result();
        if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            r.timedOut = true;
            p.destroyForcibly().waitFor();
            failures++;
            System.out.println("  FAIL [" + currentTest + "]: GenesetTools " + args[0] + " still running after " + timeoutSeconds + " s (hung)");
        }
        r.exit = r.timedOut ? -1 : p.exitValue();
        r.out = new String(Files.readAllBytes(outFile.toPath()), StandardCharsets.UTF_8);
        outFile.delete();
        r.ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        return r;
    }

    static List<String> uniprotProps(String url, String timeoutSeconds) {
        return Arrays.asList("-Duniprot.server=" + url, "-Duniprot.status.wait.ms=0",
                "-Duniprot.status.max.checks=5", "-Duniprot.request.timeout.seconds=" + timeoutSeconds);
    }

    static void commandLineTests() throws Exception {
        File dir = Files.createTempDirectory("genesettools_cli").toFile();
        File gmt = new File(dir, "Human_test_UniProt.gmt");
        Files.write(gmt.toPath(), ("SET1%TestSource%1\tset one\tP04637\tP38398\n"
                + "SET2%TestSource%2\tset two\tP04637\tQ9Y6K9\n").getBytes(StandardCharsets.UTF_8));
        File translated = new File(dir, "Human_test_symbol.gmt");

        test("every command exits 1 on an unknown option (#40)", () -> {
            for (String c : new String[]{"translate", "translate_synergizer", "compare", "createGo", "createHPO", "toGSEA",
                    "bulk", "compare2gmt", "ecoliinteraction", "convertGeneSets", "calculateOverlaps"}) {
                Result r = runTool(dir, Collections.emptyList(), c, "--no-such-option");
                checkEquals(1, r.exit, c + " --no-such-option exit status");
            }
        });

        test("convertGeneSets exits 1 when the GMT or homology file is missing (#40)", () -> {
            File out = new File(dir, "converted.gmt");
            Result r = runTool(dir, Collections.emptyList(), "convertGeneSets", "--gmt", new File(dir, "missing.gmt").getPath(),
                    "--homology", gmt.getPath(), "--newtaxid", "10090", "--outfile", out.getPath());
            checkEquals(1, r.exit, "exit status for a missing GMT");
            check(r.out.contains("unable to load GMT file"), "error message names the problem");
            check(!out.exists(), "no output file");
            r = runTool(dir, Collections.emptyList(), "convertGeneSets", "--gmt", gmt.getPath(),
                    "--homology", new File(dir, "missing.data").getPath(), "--newtaxid", "10090", "--outfile", out.getPath());
            checkEquals(1, r.exit, "exit status for a missing homology file");
            check(!out.exists(), "no output file");
        });

        test("translate exits 1 when the GMT is missing (#40)", () -> {
            Result r = runTool(dir, uniprotProps(server.baseUrl(), "10"), "translate", "--gmt",
                    new File(dir, "missing_UniProt.gmt").getPath(), "--organism", "9606", "--oldID", "UniProt", "--newID", "symbol");
            checkEquals(1, r.exit, "exit status");
        });

        test("translate works end to end against the (fake) web service", () -> {
            server.reset();
            translated.delete();
            Result r = runTool(dir, uniprotProps(server.baseUrl(), "10"), "translate", "--gmt", gmt.getPath(),
                    "--organism", "9606", "--oldID", "UniProt", "--newID", "symbol");
            checkEquals(0, r.exit, "exit status (output: " + r.out.trim().replaceAll("\\s+", " ") + ")");
            check(translated.exists(), "translated gmt written");
            if (translated.exists()) {
                String text = new String(Files.readAllBytes(translated.toPath()), StandardCharsets.UTF_8);
                check(text.contains("MAPPED_P04637") && text.contains("MAPPED_Q9Y6K9"), "ids translated: " + text.trim());
            }
            check(new File(dir, "Human_test_symbol_summary.log").exists(), "summary log written");
        });

        test("translate exits 1 and writes no GMT when the web service reports an error (#39)", () -> {
            server.reset();
            server.statusMode = "error";
            translated.delete();
            Result r = runTool(dir, uniprotProps(server.baseUrl(), "10"), "translate", "--gmt", gmt.getPath(),
                    "--organism", "9606", "--oldID", "UniProt", "--newID", "symbol");
            checkEquals(1, r.exit, "exit status");
            check(r.out.contains("Something is wrong with the uniprot web service"), "error message");
            check(!r.out.contains("NullPointerException"), "stops with the web service error, not a later NullPointerException");
            check(!translated.exists(), "no partial GMT written");
        });

        test("translate exits 1 when a job never finishes (#37, #39)", () -> {
            server.reset();
            server.statusMode = "running";
            translated.delete();
            Result r = runTool(dir, uniprotProps(server.baseUrl(), "10"), "translate", "--gmt", gmt.getPath(),
                    "--organism", "9606", "--oldID", "UniProt", "--newID", "symbol");
            checkEquals(1, r.exit, "exit status");
            check(!translated.exists(), "no partial GMT written");
        });

        test("translate gives up on a server that never answers (#37 timeout)", () -> {
            server.reset();
            server.runMode = "hang";
            translated.delete();
            Result r = runTool(30, dir, uniprotProps(server.baseUrl(), "2"), "translate", "--gmt", gmt.getPath(),
                    "--organism", "9606", "--oldID", "UniProt", "--newID", "symbol");
            checkEquals(1, r.exit, "exit status");
            check(r.ms < 60000, "gave up after the 2 s request timeout (took " + r.ms + " ms)");
            check(!translated.exists(), "no GMT written");
            server.runMode = "ok";
        });

        test("translate exits 1 when uniprot can't be reached (#39)", () -> {
            int closedPort;
            try (ServerSocket s = new ServerSocket(0)) { closedPort = s.getLocalPort(); }
            translated.delete();
            Result r = runTool(dir, uniprotProps("http://127.0.0.1:" + closedPort + "/idmapping", "10"), "translate",
                    "--gmt", gmt.getPath(), "--organism", "9606", "--oldID", "UniProt", "--newID", "symbol");
            checkEquals(1, r.exit, "exit status");
            check(!translated.exists(), "no GMT written");
        });
    }

    /**
     * A fake UniProt ID mapping service (just enough of /run, /status and /results).
     * Each id maps to "MAPPED_<id>"; results are paged 400 at a time with a 'link' header.
     */
    static class FakeUniprot {
        static class Run {
            String from, to, taxId;
            List<String> ids;
        }

        final HttpServer http;
        final List<Run> runs = new CopyOnWriteArrayList<>();
        final Map<String, List<String>> jobs = new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> statusChecks = new ConcurrentHashMap<>();
        volatile String runMode = "ok";       // ok | noJobId | hang
        volatile String statusMode = "ok";    // ok (RUNNING once, then FINISHED) | running | error
        final AtomicInteger jobCounter = new AtomicInteger();

        FakeUniprot() throws IOException {
            http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.createContext("/idmapping/run", this::run);
            http.createContext("/idmapping/status/", this::status);
            http.createContext("/idmapping/results/", this::results);
            http.setExecutor(Executors.newCachedThreadPool(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; }));
            http.start();
        }

        String baseUrl() { return "http://127.0.0.1:" + http.getAddress().getPort() + "/idmapping"; }

        void reset() {
            runs.clear(); jobs.clear(); statusChecks.clear();
            runMode = "ok"; statusMode = "ok";
        }

        void stop() { http.stop(0); }

        static void send(HttpExchange ex, int code, String body) throws IOException {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(code, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        }

        void run(HttpExchange ex) throws IOException {
            String body;
            try (InputStream is = ex.getRequestBody()) { body = new String(is.readAllBytes(), StandardCharsets.UTF_8); }
            if ("hang".equals(runMode)) {
                try { Thread.sleep(600_000); } catch (InterruptedException ignored) { }
                return;
            }
            Map<String, String> form = new HashMap<>();
            for (String kv : body.split("&")) {
                String[] p = kv.split("=", 2);
                form.put(URLDecoder.decode(p[0], "UTF-8"), p.length > 1 ? URLDecoder.decode(p[1], "UTF-8") : "");
            }
            Run r = new Run();
            r.from = form.get("from"); r.to = form.get("to"); r.taxId = form.get("taxId");
            r.ids = Arrays.asList(form.getOrDefault("ids", "").split(","));
            runs.add(r);
            if ("noJobId".equals(runMode)) {
                send(ex, 400, "{\"url\":\"/idmapping/run\",\"messages\":[\"Invalid request\"]}");
                return;
            }
            String id = "job" + jobCounter.incrementAndGet();
            jobs.put(id, r.ids);
            send(ex, 200, "{\"jobId\":\"" + id + "\"}");
        }

        void status(HttpExchange ex) throws IOException {
            String id = ex.getRequestURI().getPath().substring("/idmapping/status/".length());
            int n = statusChecks.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
            if ("error".equals(statusMode)) {
                send(ex, 200, "{\"jobStatus\":\"ERROR\",\"errors\":[{\"code\":50,\"message\":\"Internal server error.\"}]}");
            } else if ("running".equals(statusMode) || n == 1) {
                send(ex, 200, "{\"jobStatus\":\"RUNNING\"}");
            } else {
                send(ex, 200, "{\"jobStatus\":\"FINISHED\"}");
            }
        }

        void results(HttpExchange ex) throws IOException {
            String id = ex.getRequestURI().getPath().substring("/idmapping/results/".length());
            String q = ex.getRequestURI().getQuery();
            int cursor = (q != null && q.startsWith("cursor=")) ? Integer.parseInt(q.substring(7)) : 0;
            List<String> ids = jobs.getOrDefault(id, Collections.emptyList());
            int end = Math.min(cursor + 400, ids.size());
            JSONArray results = new JSONArray();
            for (String i : ids.subList(cursor, end)) {
                JSONObject o = new JSONObject();
                o.put("from", i);
                o.put("to", "MAPPED_" + i);
                results.add(o);
            }
            JSONObject page = new JSONObject();
            page.put("results", results);
            if (end < ids.size()) {
                ex.getResponseHeaders().add("link", "<" + baseUrl() + "/results/" + id + "?cursor=" + end + ">; rel=\"next\"");
            }
            send(ex, 200, page.toJSONString());
        }
    }
}
