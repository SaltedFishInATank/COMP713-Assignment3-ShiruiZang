"""Rebuild the two assessment PDFs from truthful, recorded report facts.

Optional input: ../evidence/report-facts.json. Missing facts remain explicitly unverified.
Run with the bundled Python (reportlab required). No personal reflection is fabricated.
"""
from pathlib import Path
import json
from html import escape
from reportlab.pdfgen import canvas
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, KeepTogether, Image
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_LEFT
from reportlab.graphics.shapes import Drawing, Rect, String, Line, Polygon

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "reports"
FACTS_PATH = ROOT / "evidence" / "report-facts.json"
facts = json.loads(FACTS_PATH.read_text(encoding="utf-8-sig")) if FACTS_PATH.exists() else {}
NAVY = colors.HexColor("#16324f")
TEAL = colors.HexColor("#087f8c")
LIGHT = colors.HexColor("#edf4f7")
INK = colors.HexColor("#233445")
MUTED = colors.HexColor("#526675")
AMBER = colors.HexColor("#fff1d2")
styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name="ReportTitle", fontName="Helvetica-Bold", fontSize=24, leading=29, textColor=NAVY, spaceAfter=16))
styles.add(ParagraphStyle(name="Deck", fontName="Helvetica", fontSize=12, leading=17, textColor=MUTED, spaceAfter=16))
styles.add(ParagraphStyle(name="SectionTitle", fontName="Helvetica-Bold", fontSize=15, leading=20, textColor=NAVY, spaceBefore=14, spaceAfter=9))
styles.add(ParagraphStyle(name="SubTitle", fontName="Helvetica-Bold", fontSize=10.7, leading=15, textColor=TEAL, spaceBefore=9, spaceAfter=5))
styles.add(ParagraphStyle(name="BodyTextA3", fontName="Helvetica", fontSize=10.0, leading=14.3, textColor=INK, spaceAfter=7))
styles.add(ParagraphStyle(name="SmallA3", fontName="Helvetica", fontSize=8.4, leading=11.5, textColor=MUTED, spaceAfter=5))
styles.add(ParagraphStyle(name="CellA3", fontName="Helvetica", fontSize=8.2, leading=10.5, textColor=INK))
styles.add(ParagraphStyle(name="CellHeadA3", fontName="Helvetica-Bold", fontSize=8.3, leading=11, textColor=colors.white))

def p(text, style="BodyTextA3"):
    return Paragraph(text, styles[style])

def e(value):
    return escape(str(value)).replace("\n", "<br/>")

def table(headers, rows, widths):
    data = [[p(e(v), "CellHeadA3") for v in headers]]
    data.extend([[p(e(v), "CellA3") for v in row] for row in rows])
    t = Table(data, colWidths=widths, repeatRows=1, hAlign="LEFT")
    t.setStyle(TableStyle([
        ("BACKGROUND", (0,0), (-1,0), NAVY),
        ("VALIGN", (0,0), (-1,-1), "TOP"),
        ("ROWBACKGROUNDS", (0,1), (-1,-1), [colors.white, LIGHT]),
        ("LINEBELOW", (0,0), (-1,0), .5, NAVY),
        ("BOTTOMPADDING", (0,0), (-1,-1), 6),
        ("TOPPADDING", (0,0), (-1,-1), 6),
        ("LEFTPADDING", (0,0), (-1,-1), 8),
        ("RIGHTPADDING", (0,0), (-1,-1), 8),
    ]))
    return t

def callout(text, background=LIGHT):
    t = Table([[p(text)]], colWidths=[475])
    t.setStyle(TableStyle([("BACKGROUND", (0,0),(-1,-1),background), ("BOX",(0,0),(-1,-1),.5,colors.HexColor("#c9d8df")), ("LEFTPADDING",(0,0),(-1,-1),12), ("RIGHTPADDING",(0,0),(-1,-1),12), ("TOPPADDING",(0,0),(-1,-1),11), ("BOTTOMPADDING",(0,0),(-1,-1),6)]))
    return t

def footer(label):
    def draw(c, doc):
        w,h = doc.pagesize
        c.setStrokeColor(TEAL)
        c.setLineWidth(1.6)
        c.line(48,h-31,w-48,h-31)
        c.setFont("Helvetica",7.7)
        c.setFillColor(MUTED)
        c.drawString(48,31,"COMP713 Assessment 3  |  Shirui Zang 21136179  |  31 October 2026")
        c.drawRightString(w-48,31,f"{label}  |  {doc.page}")
    return draw

def architecture():
    d = Drawing(475,244)
    def box(x,y,w,h,title,subtitle=""):
        d.add(Rect(x,y,w,h,rx=5,fillColor=LIGHT,strokeColor=TEAL,strokeWidth=1))
        d.add(String(x+w/2,y+h/2+4,title,textAnchor="middle",fontName="Helvetica-Bold",fontSize=9,fillColor=NAVY))
        if subtitle:
            d.add(String(x+w/2,y+h/2-10,subtitle,textAnchor="middle",fontName="Helvetica",fontSize=7.5,fillColor=MUTED))
    def arrow(x1,y1,x2,y2,label="",dx=0,dy=0):
        d.add(Line(x1,y1,x2,y2,strokeColor=MUTED,strokeWidth=1))
        if abs(x2-x1)>abs(y2-y1):
            sign=1 if x2>x1 else -1
            d.add(Polygon([x2,y2,x2-sign*6,y2+3,x2-sign*6,y2-3],fillColor=MUTED,strokeColor=MUTED))
        else:
            sign=1 if y2>y1 else -1
            d.add(Polygon([x2,y2,x2-3,y2-sign*6,x2+3,y2-sign*6],fillColor=MUTED,strokeColor=MUTED))
        if label:
            d.add(String((x1+x2)/2+dx,(y1+y2)/2+dy,label,textAnchor="middle",fontName="Helvetica",fontSize=7.5,fillColor=MUTED))
    box(0,185,130,46,"Web browser","Coordinator / worker")
    box(345,185,130,46,"Native Android","Field worker + location")
    box(150,115,175,52,"Payara 6 / Jakarta EE10","REST -> service -> repository")
    box(0,30,136,49,"Backend H2","Users, tasks, outbox")
    box(170,30,136,49,"OpenMQ broker","Persistent TaskEventsQueue")
    box(340,30,135,49,"Consumer JVM","Own notification H2")
    arrow(130,207,190,167,"HTTP / JSON",-20,10)
    arrow(345,207,285,167,"HTTP / JSON",20,10)
    arrow(150,140,65,79,"JDBC transactions",-15,5)
    arrow(238,115,238,79,"JMS",18,0)
    arrow(306,54,340,54,"deliver",0,10)
    d.add(String(235,5,"Consumer saves once, then acknowledges; WebSocket prompts current-state REST reads.",textAnchor="middle",fontName="Helvetica",fontSize=7.8,fillColor=MUTED))
    d.scale(1,.9)
    d.height=220
    return d

version = facts.get("finalVersion", "Final source version not yet recorded; inspect the final README and Git history.")
repository = facts.get("repository", "Public repository publication has not been verified.")
runtime = facts.get("runtimeSummary", "A3 runtime verification has not yet been recorded in report-facts.json. No deployment or workflow success is inferred from prepared source.")
android = facts.get("androidSummary", "Native Android source is provided. Build, installation, device execution and a complete real workflow must each be supported by their own final evidence.")
default_checks = [
    ("Backend build/tests", "Maven verify packages a valid WAR; no failed tests"),
    ("Actual Payara deployment", "Fresh /comp713-a3 deployment and health response"),
    ("Role/assignment access", "Coordinator controls assignment/review; workers access only assigned tasks"),
    ("Assignment/acceptance", "Coordinator assignment appears on native Android; worker accepts"),
    ("Submission/review", "Android submits; coordinator approves or requests rework"),
    ("Invalid/conflicting requests", "Controlled 400/401/403/404/409 responses as appropriate"),
    ("Persistence", "Same saved record survives a real server restart"),
    ("Mobile capability", "Actual location or explicit manual fallback with permission feedback"),
    ("WebSocket live update", "Android mutation updates open web client without refresh/polling"),
    ("Reconnect recovery", "Restored connection retrieves current authoritative state"),
    ("Independent broker consumer", "Real event creates a durable notification in another JVM"),
    ("Retry/duplicate case", "Redelivery acknowledged; one durable effect for stable event ID"),
    ("Communication failure", "Unavailable component produces feedback and recovery"),
    ("Response-time NFR", "Justified target, stated test conditions and observed distribution"),
]
checks = facts.get("checks", [dict(name=n,expected=x,actual="Not yet recorded",status="NOT RUN",evidence="No executed evidence supplied") for n,x in default_checks])
limitations = facts.get("limitations", [])
common_limits = [
    "The official brief describes a group of 2-3; this is a solo project requested by the student. No approval or teammates are invented.",
    "All current work is dated honestly. Two genuine working checkpoints are recorded on the current day; no supplied history establishes regular attributable Assessment 3 development across three weeks.",
    "A public repository and a genuine readable video of no more than 12 minutes require their own accessible deliverables; local code and a recording plan are insufficient.",
    "Single Payara instance and embedded H2; no replication, horizontal database scaling or production authentication/account administration is claimed.",
    "WebSocket restores current state rather than replaying history. The broker consumer handles at-least-once delivery; DB and messaging commits are separate.",
]

def build_group():
    story = []
    story += [p("Fieldwork", "ReportTitle"), p("Project and Task Manager | Assessment 3 group technical report\nSolo implementation: Shirui Zang | 21136179".replace("\n","<br/>"),"Deck")]
    story += [callout("<b>Verification scope.</b> "+e(runtime)), Spacer(1,10)]
    story += [p("1  Project overview and starting point", "SectionTitle")]
    story += [p("Campus maintenance teams need to assign field work, receive evidence of completion and decide whether the work is acceptable. A coordinator manages projects and assignments; field workers use a native Android client to accept and submit assigned tasks. The web client supports coordinator review and worker access. Both clients communicate with the same Payara API and authoritative relational state.")]
    story += [p("The selected Assessment 2 project is Shirui Zang's Project and Task Manager, baseline commit <b>d9b2ec58db73831fb5e1dde26961381d37253734</b>, dated 31 October 2026. Its existing Java/Jakarta REST, service, JDBC repository, H2 database and Project/Task domain are retained and extended. The older Flask implementation mentioned in historical notes is not the selected current baseline. Assessment 2 execution evidence does not certify the new features.")]
    story += [table(["Connected workflow", "User interaction and shared consequence"],[
        ["Assignment and acceptance", "Coordinator creates/assigns a todo task. Only its assigned worker may load and accept it as in_progress. The saved change becomes visible to the coordinator."],
        ["Submission and review", "Worker submits a completion note and location. Coordinator sees submitted work live, approves done or requests in_progress rework. The saved review changes the worker's next permitted action."],
    ],[135,340])]
    story += [p("The mobile capability is location-supported completion with runtime permission handling and a manual coordinate fallback. The user additionally requires actual Payara Tools deployment and WebSocket behavior, so these remain mandatory implementation goals even though the assignment describes WebSocket as bonus functionality.")]
    story += [p("Final version: "+e(version),"SmallA3"),p("Repository: "+e(repository),"SmallA3")]
    story += [p("Requirements: Assignment 2/Assignment_3.pdf, pp.1-4. Run/build instructions: root README and docs/payara-deployment.md. Exact supplied course paths/pages and baseline provenance: docs/course-audit.md.","SmallA3")]
    story += [PageBreak(),p("2  Architecture and communication", "SectionTitle"),architecture()]
    story += [p("Payara 6 hosts the Maven WAR at <b>/comp713-a3</b>. Jakarta REST translates HTTP/JSON into authenticated service calls; business logic validates roles, task transitions and optimistic versions; repositories bind SQL parameters. Explicit JDBC transactions commit related changes together. Jakarta CDI supplies managed application components, while an EJB dispatcher publishes committed outbox events through Jakarta Messaging. Jakarta WebSocket pushes relevant change notifications to authenticated open web clients.")]
    story += [p("The backend owns users, sessions, projects, tasks and event_outbox. Its persistent H2 file is outside the WAR under the domain's lib/databases/comp713-a3 directory, or an explicit comp713.a3.db.path. The independent consumer owns a different notification database, never reads backend tables and receives only contracted task.submitted JSON. Its stable eventId links attempts and deduplicated results. Physical task IDs in notifications are references, without a cross-service foreign key.")]
    story += [p("<b>Important design decision:</b> retain the verified Assessment 2 JDBC architecture rather than introduce ORM during a workflow extension. This preserves familiar transaction boundaries and a small explicit query set. The course database lecture permits both JDBC and Jakarta Persistence (Class06, p.22); the user's wording is JPA where appropriate. The trade-off is more manual SQL/mapping and no Payara-managed JDBC pool. The report does not claim JPA or the teaching MySQL datasource.")]
    story += [p("Performance review identified repeated H2 file-database shutdown/reopen when the last operation connection closed. The final source uses a bounded ten-second DB_CLOSE_DELAY and an application shutdown hook while retaining per-operation connection closure. This follows <link href='https://h2database.com/html/performance.html' color='#087f8c'>H2 performance guidance</link>; <link href='https://h2database.com/html/commands.html#set_db_close_delay' color='#087f8c'>H2 command documentation</link> defines the positive delay in seconds. Preserved failed runs and final retest results below determine whether the acceptance target was met.","SmallA3")]
    story += [p("<b>Failure behavior:</b> a task submission and outbox row commit atomically. If broker publication fails, the pending row remains for retry. Publication may repeat after an uncertain acknowledgement; the consumer saves one effect for its event ID before committing its broker delivery. A failed processing attempt rolls back and retries. The embedded OpenMQ broker is real broker software in Payara's process; the consumer is a separate JVM. This is Path B, rather than two independent gRPC services.")]
    story += [p("An authenticated WebSocket notification prompts the browser to retrieve current API state; it is not authoritative storage or durable replay. After reconnecting/authentication, fetch a fresh snapshot and prevent stale responses replacing a newer view. Client timeouts leave uncertain writes: reload state before retrying. Android keeps HTTP work away from the UI thread and shows loading/error feedback.")]
    story += [PageBreak(),p("3  Implementation status", "SectionTitle")]
    statuses = facts.get("implementation", [
        ["Retained Project/Task CRUD and JDBC persistence", "Implemented; final A3 runtime evidence below determines tested scope."],
        ["Authentication, roles, assignment/review, task versions", "Implemented; verification status must follow actual checks."],
        ["Web client and authenticated WebSocket recovery", "Implemented; final browser/device interaction evidence required."],
        ["Native Android and location/manual fallback", android],
        ["Outbox, real OpenMQ and independent consumer", "Implemented; broker/consumer/reliability results must be recorded."],
        ["Three-week progress and narrated video", "Not established by generated source or current-day commits."],
    ])
    story += [table(["Significant function", "Current status / evidence limit"],statuses,[190,285])]
    story += [p("Use the assignment's four status categories when judging the final evidence: completed and tested; completed but partially tested; partially completed; not completed. Source implementation and prepared tests alone establish neither a deployed workflow nor an observed mobile capability. The final test record below is authoritative for this report's executed scope.")]
    story += [p("4  Testing and results", "SectionTitle"),p("Executed results below distinguish unit, deployed API, actual browser, native emulator and independent-consumer evidence. Earlier failures remain preserved with the final passing records.")]
    rows=[]
    for c in checks:
        rows.append([c.get("name",""),c.get("expected",""),c.get("status","")+": "+c.get("actual","")+"\nEvidence: "+c.get("evidence","")])
    story += [table(["Check", "Expected", "Actual / supporting evidence"],rows,[115,160,200])]
    nfr=facts.get("nfr", "Response-time acceptance target and measurements have not yet been supplied. Record a justified target for a representative deployed user operation, number of samples, warm-up, concurrency, environment and actual observed distribution. Do not label a target as a result.")
    story += [p("Response-time requirement", "SubTitle"),p(e(nfr))]
    story += [p("Tests should cover retained behavior, both real clients, shared workflows, invalid input, role failure, persistence, live updates/reconnect and broker reliability. Native device checks and a browser workflow remain distinct from service unit tests and host-side API requests. Evidence identifiers refer to the code submission's evidence directory unless stated otherwise.")]
    story += [PageBreak(),p("5  Contributions and progress", "SectionTitle")]
    story += [p("The actual development arrangement is solo. OpenAI Codex assisted with implementation, review, verification scripts and documentation. The student must review and understand all submitted code and explain the complete communication flow. This report records tool-observed technical work; it does not invent personal student activity, teammates or historical progress.")]
    story += [table(["Area", "Traceable files / evidence"],[
        ["Backend and persistence", "backend/pom.xml; api, service, repository and persistence packages; schema; deployed/API evidence."],
        ["Web and native mobile", "backend/src/main/webapp; android/app/src/main; build, browser and device evidence."],
        ["Advanced integration", "messaging/TaskEventPublisher; consumer/src/main; separate process, notification and retry/duplicate logs."],
        ["Reproducibility/analysis", "README; docs; scripts/tests; evidence; actual final commit history and reports."],
    ],[145,330])]
    story += [p("The Assessment 2 baseline and new Assessment 3 changes must remain distinguishable in repository history. The brief requires regular attributable Assessment 3 commits across at least three weeks and an Android-to-API checkpoint followed by a later WebSocket/advanced prototype. Current-day work cannot satisfy elapsed historical development. docs/progress.md preserves the genuine dates and any final checkpoint evidence; no commits are backdated.")]
    if facts.get("checkpoints"):
        story += [table(["Dated working checkpoint", "Observed evidence"],facts["checkpoints"],[155,320])]
    story += [p("6  Limitations and improvements", "SectionTitle")]
    for text in limitations:
        story += [p("- "+e(text))]
    story += [p("Outstanding submission requirements are solo/group approval, a publicly accessible GitHub repository, the required three-week development history, a genuine narrated video of no more than 12 minutes and the student's independently written personal report. Two current-day checkpoints cannot establish historical progress. No teammates, backdated commits or completed recording are invented.")]
    story += [p("One Payara/H2 instance has no verified replication or production capacity. WebSocket restores current state; broker delivery is at least once with idempotent effects. After student review, publication and recording, priorities for broader use are HTTPS, a managed datasource and monitoring.")]
    web_image=ROOT/"evidence/web/android-live-submitted-evidence.png"
    android_image=ROOT/"android/evidence/screenshots/08-device-gps-captured.png"
    if web_image.exists() and android_image.exists():
        story += [PageBreak(),p("Appendix  Selected genuine screens", "SectionTitle")]
        story += [Image(str(web_image),width=475,height=329.86),p("Figure A1. Open coordinator web client shows the actual native Android task6 completion note and manual site coordinates. The observer recorded TO DO -> IN PROGRESS -> AWAITING REVIEW without page reload (web/android-observer-result.json).", "SmallA3")]
        native=Image(str(android_image),width=138,height=299)
        caption=p("<b>Figure A2. Native Android location capability.</b><br/><br/>The app received a fresh LocationManager fix after runtime permission was granted, displayed accuracy and populated coordinates for task8. The emulator's injected test location was used; this is not a physical field measurement.<br/><br/>The native runner separately exercised permission denial/manual fallback, real HTTP401 feedback, an actual409 conflict with draft retention, submission and coordinator approval refresh.<br/><br/>Source: project android/evidence/screenshots/08-device-gps-captured.png and native-runtime.log. The task8 event also reached the independent consumer (broker/android/result.json).", "SmallA3")
        figures=Table([[native,caption]],colWidths=[153,322],hAlign="LEFT")
        figures.setStyle(TableStyle([("VALIGN",(0,0),(-1,-1),"TOP"),("LEFTPADDING",(0,0),(-1,-1),0),("RIGHTPADDING",(0,0),(-1,-1),12)]))
        story += [figures]
    path=OUT/"COMP713_Assessment3_Group_Report_ShiruiZang_21136179.pdf"
    SimpleDocTemplate(str(path),pagesize=(595.28,841.89),rightMargin=60,leftMargin=60,topMargin=52,bottomMargin=51,title="COMP713 Assessment 3 Group Technical Report",author="Shirui Zang; prepared with OpenAI Codex assistance").build(story,onFirstPage=footer("Group report"),onLaterPages=footer("Group report"))
    return path

def build_individual():
    story=[p("Individual technical report", "ReportTitle"),p("DRAFT - student review and independent writing required", "Deck"),callout("<b>This is AI-prepared support material, not an independently written personal submission.</b> The brief requires an independently written individual report. Shirui Zang must replace the reflection/contribution prompts with accurate first-person analysis, review all factual statements and disclose assistance as required. No personal experience or opinion is invented.",AMBER),Spacer(1,12)]
    story += [p("Student: Shirui Zang | ID: 21136179 | COMP713 Assessment3<br/>Development arrangement: solo, subject to the course's group requirement.<br/>Final version: "+e(version),"SmallA3")]
    story += [p("1  System understanding - factual starting draft", "SectionTitle")]
    story += [p("A coordinator assigns a maintenance task in the web client. The client sends authenticated HTTP/JSON to Jakarta REST on Payara 6. Service logic checks role and input; JDBC repository operations save the task in H2. The assigned worker retrieves that state in the native Android app, accepts the task and later submits completion evidence. The server enforces workflow transitions and optimistic versions so a stale update returns409 instead of silently overwriting newer state.")]
    story += [p("Submission saves the task and a stable-ID task.submitted outbox event in the same database transaction. A Payara EJB dispatcher publishes pending events to real OpenMQ. A separately running Java consumer saves a notification in its own H2 database and then commits the broker delivery. A redelivery may repeat the message, but the event ID protects the persistent business effect. The backend's data and consumer's data have separate ownership and files.")]
    story += [p("The server also sends a WebSocket change notification after committing relevant state. An open web client retrieves current server data automatically. On reconnection it authenticates and restores current state; the live connection does not itself provide missed history. The coordinator reviews the submission and may approve it or request rework, changing what the worker can do next. The mobile capability supplies device location with permission handling and a manual fallback.")]
    story += [p("Student verification task", "SubTitle"),p("Rewrite this explanation in your own words after tracing one real task ID through Android, Payara, H2, WebSocket and consumer output. Cite the final files and evidence you personally checked. Correct any implementation detail that changed.")]
    story += [PageBreak(),p("2  Critical reflection - write independently", "SectionTitle")]
    story += [p("The following are prompts and factual trade-offs for analysis, rather than a fabricated reflection. Choose an issue you actually investigated and explain the evidence that changed or confirmed your decision.")]
    prompts=[
        ("Design trade-off", "Why retain the Assessment 2 JDBC/service architecture? Compare lower migration risk and explicit SQL control with manual mapping and the absence of a managed pool/JPA. State your own judgment and the exact code you reviewed."),
        ("Reliability boundary", "Trace the task/outbox transaction, broker publication and consumer DB/JMS commits. Explain the failure window after a DB save but before acknowledgement. Use an actual retry/duplicate run to support your reasoning."),
        ("Client recovery", "Explain how a disconnected web/mobile client learns current state and handles uncertain writes. Describe an observed failure rather than asserting that reconnection always succeeds."),
        ("Testing insight", "Select one executed check that exposed a defect, limitation or unexpected behavior. State expected versus actual results, the cause, what changed and the retest outcome. If there was no such observation, say so."),
        ("Performance", "Justify the selected response-time target and describe test conditions/results. Explain why local sequential API latency does not establish mobile-network performance or production scalability."),
        ("AI-generated code review", "Identify generated code you reviewed. Explain concrete checks: authorization transitions, SQL binding/rollback, task version conflicts, consumer duplicate protection and Android permission/error states. Name what you did not independently verify."),
        ("Improvement priority", "Choose one feasible next change tied to observed evidence. Explain its benefit, cost and how you would verify it, rather than listing generic features."),
    ]
    for title,text in prompts:
        story += [p(title,"SubTitle"),p(e(text))]
    story += [p("Final recorded runtime scope: "+e(runtime),"SmallA3"),p("Android scope: "+e(android),"SmallA3")]
    story += [PageBreak(),p("3  Personal contribution - evidence worksheet", "SectionTitle")]
    story += [p("The official brief describes three enumerated individual-report sections despite saying four. Cover system understanding, critical reflection and personal contribution explicitly. Do not present this worksheet as completed personal authorship.")]
    story += [table(["Claim to write only if true", "Evidence to supply"],[
        ["A specific new Assessment 3 contribution", "Name the files/lines and real commits you worked on; explain the decision and its effect. Distinguish tool-generated code from your own edits/review."],
        ["A technical test you performed/reviewed", "Link actual command/output, expected/actual result, date and final version. Host-side API checks differ from native Android execution."],
        ["A problem you diagnosed", "Describe the observed symptom, competing causes, diagnostic evidence, change and retest."],
        ["A checkpoint and ongoing progress", "Use real dated working evidence only. Current-day commits do not establish three weeks; do not backdate or claim a teammate's work."],
        ["Understanding AI assistance", "State what Codex generated, how you reviewed/tested it, and where independent verification remains incomplete."],
    ],[190,285])]
    story += [p("Baseline distinction", "SubTitle"),p("Assessment 2 already supplied the Project/Task domain, Jakarta REST registration, service/repository/JDBC persistence, validation/error patterns and browser behavior. Its selected baseline is d9b2ec58db73831fb5e1dde26961381d37253734. Assessment 3 introduces roles/sessions, assignment/review, versions, completion location, native Android, WebSocket recovery and broker/consumer integration. Explain your actual contribution to those changes with precise evidence.")]
    story += [p("Before individual submission", "SubTitle"),p("Write the report independently; replace the prompts, verify factual claims against final code/evidence, state limitations and solo/group status, disclose assistance, add the accessible repository and final version, and export a separate final PDF/DOCX outside the code ZIP. Remove DRAFT only after the resulting report is actually your independently written work.")]
    story += [p("Source requirements: Assignment 2/Assignment_3.pdf, pp.1,3-4. Shared technical evidence: root README, docs/course-audit.md, docs/progress.md and evidence. The main group report is a separate deliverable.","SmallA3")]
    path=OUT/"COMP713_Assessment3_Individual_Report_DRAFT_ShiruiZang_21136179.pdf"
    SimpleDocTemplate(str(path),pagesize=(595.28,841.89),rightMargin=60,leftMargin=60,topMargin=52,bottomMargin=51,title="DRAFT COMP713 Assessment 3 Individual Report Support",author="AI-prepared support for Shirui Zang").build(story,onFirstPage=footer("Individual DRAFT"),onLaterPages=footer("Individual DRAFT"))
    return path

if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    for result in [build_group(),build_individual()]:
        print(result)

