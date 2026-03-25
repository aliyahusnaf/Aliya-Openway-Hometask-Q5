package periplus.report;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

// builds a test case execution report in both console and LaTeX format.

public class TestCaseReport {

    private static final Logger log = Logger.getLogger(TestCaseReport.class.getName());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // inner class for a single step

    public static class Step {
        public final int number;
        public final String description;
        public String status = "NOT RUN";
        public String detail  = "";

        Step(int number, String description) {
            this.number      = number;
            this.description = description;
        }
    }

    // report fields

    private final String id;
    private final String title;
    private final String priority;
    private final String module;
    private final List<String> preconditions = new ArrayList<>();
    private final List<Step>   steps         = new ArrayList<>();
    private       String expectedResult      = "";
    private       String actualResult        = "";
    private       String status              = "NOT RUN";
    private final String startTime;
    private       String endTime             = "";

    public TestCaseReport(String id, String title, String priority, String module) {
        this.id        = id;
        this.title     = title;
        this.priority  = priority;
        this.module    = module;
        this.startTime = LocalDateTime.now().format(FMT);
    }

    // builder-style setup

    public TestCaseReport addPrecondition(String text) {
        preconditions.add(text);
        return this;
    }

    public TestCaseReport addStep(String description) {
        steps.add(new Step(steps.size() + 1, description));
        return this;
    }

    public TestCaseReport setExpectedResult(String result) {
        this.expectedResult = result;
        return this;
    }

    // called during test execution

    public void passStep(int stepNum) {
        passStep(stepNum, "");
    }

    public void passStep(int stepNum, String detail) {
        Step s = find(stepNum);
        if (s != null) { s.status = "PASS"; s.detail = detail; }
    }

    public void failStep(int stepNum, String detail) {
        Step s = find(stepNum);
        if (s != null) { s.status = "FAIL"; s.detail = detail; }
    }

    public void setActualResult(String result) {
        this.actualResult = result;
    }

    public void setStatus(String status) {
        this.status  = status;
        this.endTime = LocalDateTime.now().format(FMT);
    }

    public String getId() {
        return id;
    }

    public List<Step> getSteps() {
        return steps;
    }

    // output

    // prints a plain-text summary to the console log
    public void printToLog() {
        log.info("\n" + buildTextSummary());
    }

    // generates a single FULL_REPORT.tex that contains every test case in order
    
    public static String saveCombinedLatex(List<TestCaseReport> reports, String outputPath)
            throws IOException {
        File dir = new File(outputPath).getParentFile();
        if (dir != null) dir.mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter(outputPath))) {
            pw.print(buildCombinedDocument(reports));
        }
        return outputPath;
    }

    private static String buildCombinedDocument(List<TestCaseReport> reports) {
        long passed = reports.stream().filter(r -> "PASS".equals(r.status)).count();
        long failed = reports.size() - passed;
        String generated = LocalDateTime.now().format(FMT);

        StringBuilder sb = new StringBuilder();

        // preamble
        sb.append("\\documentclass[a4paper,11pt]{article}\n");
        sb.append("\\usepackage[utf8]{inputenc}\n");
        sb.append("\\usepackage[T1]{fontenc}\n");
        sb.append("\\usepackage[margin=2.5cm]{geometry}\n");
        sb.append("\\usepackage{booktabs}\n");
        sb.append("\\usepackage{tabularx}\n");
        sb.append("\\usepackage{xcolor}\n");
        sb.append("\\usepackage{array}\n");
        sb.append("\\usepackage{enumitem}\n");
        sb.append("\\usepackage{fancyhdr}\n");
        sb.append("\\usepackage{hyperref}\n");
        sb.append("\\usepackage{titlesec}\n\n");

        sb.append("\\definecolor{passgreen}{RGB}{46,125,50}\n");
        sb.append("\\definecolor{failred}{RGB}{198,40,40}\n");
        sb.append("\\definecolor{notrun}{RGB}{120,120,120}\n\n");

        sb.append("\\newcolumntype{L}[1]{>{\\raggedright\\arraybackslash}p{#1}}\n\n");

        sb.append("\\pagestyle{fancy}\n");
        sb.append("\\fancyhf{}\n");
        sb.append("\\lhead{\\textcolor{gray}{Periplus Cart Test Report}}\n");
        sb.append("\\rhead{\\textcolor{gray}{\\leftmark}}\n");
        sb.append("\\rfoot{\\thepage}\n\n");

        sb.append("\\begin{document}\n\n");

        // cover page 
        sb.append("\\begin{titlepage}\n");
        sb.append("\\centering\n");
        sb.append("\\vspace*{3cm}\n");
        sb.append("{\\Huge\\textbf{Test Execution Report}}\\\\[1em]\n");
        sb.append("{\\Large Periplus Shopping Cart}\\\\[0.5em]\n");
        sb.append("\\noindent\\rule{10cm}{0.6pt}\\\\[2em]\n");
        sb.append("\\begin{tabular}{ll}\n");
        sb.append("  \\textbf{Generated} & " + tex(generated) + " \\\\\n");
        sb.append("  \\textbf{Total Tests} & " + reports.size() + " \\\\\n");
        sb.append("  \\textbf{Passed} & \\textcolor{passgreen}{\\textbf{" + passed + "}} \\\\\n");
        sb.append("  \\textbf{Failed} & \\textcolor{failred}{\\textbf{" + failed + "}} \\\\\n");
        sb.append("\\end{tabular}\n");
        sb.append("\\end{titlepage}\n\n");

        // table of contents
        sb.append("\\tableofcontents\n\\clearpage\n\n");

        // summary table
        sb.append("\\section{Test Execution Summary}\n\n");
        sb.append("\\begin{tabularx}{\\textwidth}{@{}l L{7cm} c c@{}}\n");
        sb.append("  \\toprule\n");
        sb.append("  \\textbf{ID} & \\textbf{Title} & \\textbf{Duration} & \\textbf{Status} \\\\\n");
        sb.append("  \\midrule\n");
        for (TestCaseReport r : reports) {
            String statusTex = "PASS".equals(r.status)
                ? "\\textcolor{passgreen}{\\textbf{PASS}}"
                : "FAIL".equals(r.status)
                    ? "\\textcolor{failred}{\\textbf{FAIL}}"
                    : "\\textcolor{notrun}{NOT RUN}";
            sb.append(String.format("  %s & %s & %s & %s \\\\\n",
                tex(r.id), tex(r.title), tex(r.endTime), statusTex));
        }
        sb.append("  \\bottomrule\n");
        sb.append("\\end{tabularx}\n\n");
        sb.append("\\clearpage\n\n");

        // one section per test case
        for (TestCaseReport r : reports) {
            sb.append(r.buildSection());
            sb.append("\\clearpage\n\n");
        }

        sb.append("\\end{document}\n");
        return sb.toString();
    }

    // private helpers

    // generates the body of this test case as a LaTeX section
    private String buildSection() {
        StringBuilder sb = new StringBuilder();
        String statusColor = status.equals("PASS") ? "passgreen" : status.equals("FAIL") ? "failred" : "notrun";

        sb.append("\\section{" + tex(id) + " -- " + tex(title) + "}\n\n");

        // info table
        sb.append("\\begin{tabular}{@{}L{3.5cm}L{11cm}@{}}\n");
        sb.append("  \\toprule\n");
        infoRow(sb, "Test Case ID",  id);
        infoRow(sb, "Title",         title);
        infoRow(sb, "Priority",      priority);
        infoRow(sb, "Module",        module);
        infoRow(sb, "Started",       startTime);
        infoRow(sb, "Finished",      endTime);
        sb.append("  \\bottomrule\n");
        sb.append("\\end{tabular}\n\n");

        // preconditions
        sb.append("\\subsection*{Preconditions}\n");
        sb.append("\\begin{itemize}[noitemsep]\n");
        for (String p : preconditions) sb.append("  \\item " + tex(p) + "\n");
        sb.append("\\end{itemize}\n\n");

        // steps table
        sb.append("\\subsection*{Test Steps}\n");
        sb.append("\\begin{tabularx}{\\textwidth}{@{}c L{5.5cm} c X@{}}\n");
        sb.append("  \\toprule\n");
        sb.append("  \\textbf{\\#} & \\textbf{Step} & \\textbf{Status} & \\textbf{Detail} \\\\\n");
        sb.append("  \\midrule\n");
        for (Step s : steps) {
            String detail = s.detail.isEmpty() ? "---" : tex(s.detail);
            sb.append(String.format("  %d & %s & %s & {\\small %s} \\\\\n",
                s.number, tex(s.description), statusCell(s.status), detail));
        }
        sb.append("  \\bottomrule\n");
        sb.append("\\end{tabularx}\n\n");

        // results
        sb.append("\\subsection*{Expected Result}\n");
        sb.append("\\begin{quote}\n  " + tex(expectedResult) + "\n\\end{quote}\n\n");
        sb.append("\\subsection*{Actual Result}\n");
        sb.append("\\begin{quote}\n  " + tex(actualResult) + "\n\\end{quote}\n\n");

        sb.append("\\subsection*{Status}\n");
        sb.append("\\textcolor{" + statusColor + "}{\\Large\\textbf{" + tex(status) + "}}\n\n");

        return sb.toString();
    }

    private Step find(int number) {
        return steps.stream().filter(s -> s.number == number).findFirst().orElse(null);
    }

    private String buildTextSummary() {
        StringBuilder sb = new StringBuilder();
        String line = "=".repeat(65);
        sb.append(line).append("\n");
        sb.append("  TEST EXECUTION REPORT\n");
        sb.append(line).append("\n");
        sb.append(String.format("  ID       : %s%n", id));
        sb.append(String.format("  Title    : %s%n", title));
        sb.append(String.format("  Priority : %s    Module: %s%n", priority, module));
        sb.append(String.format("  Started  : %s%n", startTime));
        sb.append(String.format("  Finished : %s%n", endTime));
        sb.append(line).append("\n");

        sb.append("  Preconditions:\n");
        for (String p : preconditions) sb.append("    - ").append(p).append("\n");
        sb.append(line).append("\n");

        sb.append("  Steps:\n");
        for (Step s : steps) {
            String pad = ".".repeat(Math.max(1, 50 - s.description.length()));
            sb.append(String.format("    %d. %s%s [%s]%n", s.number, s.description, pad, s.status));
            if (!s.detail.isEmpty()) sb.append(String.format("       -> %s%n", s.detail));
        }
        sb.append(line).append("\n");

        sb.append(String.format("  Expected : %s%n", expectedResult));
        sb.append(String.format("  Actual   : %s%n", actualResult));
        sb.append(String.format("  Status   : %s%n", status));
        sb.append(line).append("\n");
        return sb.toString();
    }

    private void infoRow(StringBuilder sb, String label, String value) {
        sb.append("  \\textbf{" + tex(label) + "} & " + tex(value) + " \\\\\n");
    }

    private String statusCell(String s) {
        switch (s) {
            case "PASS":    return "\\textcolor{passgreen}{\\textbf{PASS}}";
            case "FAIL":    return "\\textcolor{failred}{\\textbf{FAIL}}";
            default:        return "\\textcolor{notrun}{NOT RUN}";
        }
    }

    // escapes special LaTeX characters
    private static String tex(String s) {
        if (s == null) return "";
        return s
            .replace("\\", "\\textbackslash{}")
            .replace("&",  "\\&")
            .replace("%",  "\\%")
            .replace("$",  "\\$")
            .replace("#",  "\\#")
            .replace("_",  "\\_")
            .replace("^",  "\\^{}")
            .replace("{",  "\\{")
            .replace("}",  "\\}")
            .replace("~",  "\\textasciitilde{}")
            .replace("\"", "''");
    }
}
