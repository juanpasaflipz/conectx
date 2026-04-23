/**
 * Conectx Peñas Lead Tracker — Google Apps Script
 *
 * HOW TO USE:
 * 1. Import penas-lead-tracker.csv into a new Google Sheet
 * 2. Go to Extensions > Apps Script
 * 3. Paste this entire file
 * 4. Click Run > setupTracker
 * 5. Authorize when prompted
 *
 * This will format the sheet, add dropdowns, conditional formatting,
 * and create a dashboard summary.
 */

function setupTracker() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName("Leads") || ss.getActiveSheet();
  sheet.setName("Leads");

  // --- FORMATTING ---
  formatLeadsSheet(sheet);

  // --- STATUS DROPDOWN ---
  addStatusDropdown(sheet);

  // --- CONDITIONAL FORMATTING ---
  addConditionalFormatting(sheet);

  // --- DASHBOARD ---
  createDashboard(ss);

  // --- OUTREACH CALENDAR ---
  createOutreachCalendar(ss);

  SpreadsheetApp.getUi().alert(
    "✅ Tracker setup complete!\n\n" +
    "• Leads sheet: formatted with dropdowns & color coding\n" +
    "• Dashboard: live funnel metrics\n" +
    "• Outreach Calendar: weekly action items\n\n" +
    "Start by updating Status column as you make contact."
  );
}

function formatLeadsSheet(sheet) {
  // Freeze header row
  sheet.setFrozenRows(1);

  // Header formatting
  const headerRange = sheet.getRange(1, 1, 1, sheet.getLastColumn());
  headerRange.setBackground("#1a1a2e")
    .setFontColor("#ffffff")
    .setFontWeight("bold")
    .setFontSize(10)
    .setWrap(true)
    .setVerticalAlignment("middle");

  // Column widths
  const widths = {
    1: 50,    // Rank
    2: 180,   // Peña Name
    3: 160,   // Team
    4: 250,   // Lieutenant Target
    5: 140,   // Primary Platform
    6: 250,   // Contact Path Primary
    7: 250,   // Contact Path Secondary
    8: 250,   // Contact Path Tertiary
    9: 120,   // Script Variant
    10: 120,  // Status
    11: 40,   // CS
    12: 40,   // GR
    13: 40,   // AP
    14: 40,   // PP
    15: 40,   // TO
    16: 80,   // Total Score
    17: 110,  // Day 0
    18: 110,  // Day 3
    19: 110,  // Day 7
    20: 110,  // Day 10
    21: 110,  // Day 14
    22: 110,  // Last Action Date
    23: 200,  // Last Action
    24: 400   // Notes
  };

  Object.entries(widths).forEach(([col, width]) => {
    sheet.setColumnWidth(parseInt(col), width);
  });

  // Score columns formatting (center align, light bg)
  const scoreRange = sheet.getRange(2, 11, sheet.getLastRow() - 1, 6);
  scoreRange.setHorizontalAlignment("center")
    .setFontWeight("bold");

  // Alternating row colors
  const dataRows = sheet.getLastRow() - 1;
  for (let i = 2; i <= dataRows + 1; i++) {
    const row = sheet.getRange(i, 1, 1, sheet.getLastColumn());
    if (i % 2 === 0) {
      row.setBackground("#f8f9fa");
    }
  }

  // Tier separator: bold line after rank 5
  if (dataRows >= 5) {
    const tierBreak = sheet.getRange(6, 1, 1, sheet.getLastColumn());
    tierBreak.setBorder(null, null, true, null, null, null, "#e94560", SpreadsheetApp.BorderStyle.SOLID_MEDIUM);
  }

  // Total Score column — highlight top scores
  const totalScoreRange = sheet.getRange(2, 16, dataRows, 1);
  totalScoreRange.setFontSize(12);
}

function addStatusDropdown(sheet) {
  const statuses = [
    "sourced",
    "qualified",
    "contacted",
    "replied",
    "intro booked",
    "pilot interested",
    "test booked",
    "test completed",
    "not a fit"
  ];

  const rule = SpreadsheetApp.newDataValidation()
    .requireValueInList(statuses, true)
    .setAllowInvalid(false)
    .build();

  const statusCol = 10; // Column J = Status
  const dataRows = sheet.getLastRow() - 1;
  sheet.getRange(2, statusCol, dataRows, 1).setDataValidation(rule);
}

function addConditionalFormatting(sheet) {
  const statusCol = 10;
  const dataRows = sheet.getLastRow();
  const range = sheet.getRange(2, 1, dataRows - 1, sheet.getLastColumn());

  // Clear existing rules
  sheet.clearConditionalFormatRules();

  const rules = [];

  // Status-based row coloring
  const statusColors = {
    "sourced": "#f5f5f5",           // light gray
    "qualified": "#fff3e0",         // light orange
    "contacted": "#e3f2fd",         // light blue
    "replied": "#e8f5e9",           // light green
    "intro booked": "#c8e6c9",     // medium green
    "pilot interested": "#a5d6a7", // green
    "test booked": "#81c784",      // strong green
    "test completed": "#4caf50",   // dark green
    "not a fit": "#ffcdd2"         // light red
  };

  Object.entries(statusColors).forEach(([status, color]) => {
    const rule = SpreadsheetApp.newConditionalFormatRule()
      .whenFormulaSatisfied(`=$J2="${status}"`)
      .setBackground(color)
      .setRanges([range])
      .build();
    rules.push(rule);
  });

  // High score highlight (24+)
  const scoreRange = sheet.getRange(2, 16, dataRows - 1, 1);
  const highScore = SpreadsheetApp.newConditionalFormatRule()
    .whenNumberGreaterThanOrEqualTo(24)
    .setBackground("#fff176")
    .setFontColor("#e65100")
    .setBold(true)
    .setRanges([scoreRange])
    .build();
  rules.push(highScore);

  sheet.setConditionalFormatRules(rules);
}

function createDashboard(ss) {
  let dashboard = ss.getSheetByName("Dashboard");
  if (dashboard) ss.deleteSheet(dashboard);
  dashboard = ss.insertSheet("Dashboard");

  // Title
  dashboard.getRange("A1").setValue("CONECTX PEÑAS PIPELINE")
    .setFontSize(18).setFontWeight("bold").setFontColor("#1a1a2e");
  dashboard.getRange("A2").setValue("Live Dashboard — updates automatically")
    .setFontColor("#666666").setFontSize(10);

  // --- FUNNEL METRICS ---
  dashboard.getRange("A4").setValue("FUNNEL STATUS")
    .setFontSize(14).setFontWeight("bold");

  const stages = [
    "sourced", "qualified", "contacted", "replied",
    "intro booked", "pilot interested", "test booked", "test completed", "not a fit"
  ];

  dashboard.getRange("A5").setValue("Stage").setFontWeight("bold");
  dashboard.getRange("B5").setValue("Count").setFontWeight("bold");
  dashboard.getRange("C5").setValue("% of Total").setFontWeight("bold");

  stages.forEach((stage, i) => {
    const row = 6 + i;
    dashboard.getRange(`A${row}`).setValue(stage);
    dashboard.getRange(`B${row}`).setFormula(
      `=COUNTIF(Leads!J:J,"${stage}")`
    );
    dashboard.getRange(`C${row}`).setFormula(
      `=IF(COUNTA(Leads!J2:J)>0,B${row}/COUNTA(Leads!J2:J),0)`
    );
    dashboard.getRange(`C${row}`).setNumberFormat("0%");
  });

  // --- KEY NUMBERS ---
  dashboard.getRange("E4").setValue("KEY NUMBERS")
    .setFontSize(14).setFontWeight("bold");

  const metrics = [
    ["Total Leads", '=COUNTA(Leads!J2:J)'],
    ["Active (not 'not a fit')", '=COUNTA(Leads!J2:J)-COUNTIF(Leads!J:J,"not a fit")'],
    ["Response Rate", '=IF(COUNTIF(Leads!J:J,"contacted")>0,(COUNTIF(Leads!J:J,"replied")+COUNTIF(Leads!J:J,"intro booked")+COUNTIF(Leads!J:J,"pilot interested")+COUNTIF(Leads!J:J,"test booked")+COUNTIF(Leads!J:J,"test completed"))/(COUNTIF(Leads!J:J,"contacted")+COUNTIF(Leads!J:J,"replied")+COUNTIF(Leads!J:J,"intro booked")+COUNTIF(Leads!J:J,"pilot interested")+COUNTIF(Leads!J:J,"test booked")+COUNTIF(Leads!J:J,"test completed")),0)'],
    ["Tests Completed", '=COUNTIF(Leads!J:J,"test completed")'],
    ["Tests Booked", '=COUNTIF(Leads!J:J,"test booked")'],
    ["Target: 3 Tests", "3"],
    ["Gap to Target", '=3-COUNTIF(Leads!J:J,"test completed")'],
    ["Avg Score (Active)", '=AVERAGEIF(Leads!J2:J,"<>not a fit",Leads!P2:P)'],
  ];

  metrics.forEach(([label, formula], i) => {
    const row = 5 + i;
    dashboard.getRange(`E${row}`).setValue(label).setFontWeight("bold");
    if (formula.startsWith("=")) {
      dashboard.getRange(`F${row}`).setFormula(formula);
    } else {
      dashboard.getRange(`F${row}`).setValue(formula);
    }
  });

  // Format response rate as percentage
  dashboard.getRange("F7").setNumberFormat("0%");
  dashboard.getRange("F12").setNumberFormat("0.0");

  // --- WEEKLY PROGRESS ---
  dashboard.getRange("A16").setValue("WEEKLY CHECKLIST")
    .setFontSize(14).setFontWeight("bold");

  const weeklyItems = [
    ["Week 1 (Apr 14-20)", "Contact leads #1-5. Verify paths. Send DMs."],
    ["Week 2 (Apr 21-27)", "Contact leads #6-10. Follow up Week 1. Push demos."],
    ["Week 3 (Apr 28-May 4)", "Warm intros. Convert to pilot interested. ID match dates."],
    ["Week 4 (May 5-11)", "Execute first match-day tests. Post-test debriefs."],
  ];

  dashboard.getRange("A17").setValue("Week").setFontWeight("bold");
  dashboard.getRange("B17").setValue("Done?").setFontWeight("bold");
  dashboard.getRange("C17").setValue("Actions").setFontWeight("bold");

  weeklyItems.forEach(([week, actions], i) => {
    const row = 18 + i;
    dashboard.getRange(`A${row}`).setValue(week);
    // Checkbox
    dashboard.getRange(`B${row}`).insertCheckboxes();
    dashboard.getRange(`C${row}`).setValue(actions);
  });

  // --- OUTREACH COPY REFERENCE ---
  dashboard.getRange("A23").setValue("OUTREACH SCRIPTS (quick reference)")
    .setFontSize(14).setFontWeight("bold");

  const scripts = [
    ["Variant A (logistica)", "Para peñas que publican logística. Leads #3, #4, #6, #9."],
    ["Variant C (visitante)", "Para grupos visitantes y tours. Leads #1, #2, #5, #7, #8, #10."],
    ["Follow-up (sin respuesta)", "Día 3: 'Hola de nuevo, sé que traen mucho...'"],
    ["Follow-up (ocupado)", "Para quien dijo 'me interesa pero ando ocupado.'"],
  ];

  scripts.forEach(([variant, desc], i) => {
    const row = 24 + i;
    dashboard.getRange(`A${row}`).setValue(variant).setFontWeight("bold");
    dashboard.getRange(`C${row}`).setValue(desc);
  });

  // Column widths
  dashboard.setColumnWidth(1, 200);
  dashboard.setColumnWidth(2, 80);
  dashboard.setColumnWidth(3, 400);
  dashboard.setColumnWidth(4, 20);
  dashboard.setColumnWidth(5, 200);
  dashboard.setColumnWidth(6, 120);

  // Dashboard background
  dashboard.getRange("A1:F28").setBackground("#ffffff");
}

function createOutreachCalendar(ss) {
  let cal = ss.getSheetByName("Outreach Calendar");
  if (cal) ss.deleteSheet(cal);
  cal = ss.insertSheet("Outreach Calendar");

  // Title
  cal.getRange("A1").setValue("OUTREACH CALENDAR")
    .setFontSize(16).setFontWeight("bold").setFontColor("#1a1a2e");

  // Headers
  const headers = ["Date", "Day", "Lead #", "Peña", "Action", "Channel", "Script", "Done?", "Result"];
  headers.forEach((h, i) => {
    cal.getRange(3, i + 1).setValue(h)
      .setFontWeight("bold").setBackground("#1a1a2e").setFontColor("#ffffff");
  });

  // Week 1 outreach schedule
  const schedule = [
    // Week 1
    ["Apr 14 (Mon)", "Day 0 prep", "1-5", "All Tier 1", "Verify contact paths, customize scripts", "—", "—", false, ""],
    ["Apr 15 (Tue)", "Day 0", "1", "Barra Insurgencia", "Send initial DM", "IG @barra_insurgencia", "C", false, ""],
    ["Apr 15 (Tue)", "Day 0", "2", "Legion 1908 DF", "Send initial DM", "IG @legion1908dfoficial", "C", false, ""],
    ["Apr 15 (Tue)", "Day 0", "5", "Safer Chivas Tour", "Send initial message", "WhatsApp 55 6187 2894", "C", false, ""],
    ["Apr 16 (Wed)", "Day 0", "3", "Ritual del Kaoz", "Send initial DM", "IG @losrk1999", "A", false, ""],
    ["Apr 16 (Wed)", "Day 0", "4", "La Sangre Azul", "Send initial DM", "IG @la.sangre.azul.official", "A", false, ""],
    ["Apr 18 (Fri)", "Day 3", "1", "Barra Insurgencia", "Follow-up if no reply", "IG (same) or Email", "Follow-up A", false, ""],
    ["Apr 18 (Fri)", "Day 3", "2", "Legion 1908 DF", "Follow-up if no reply", "IG (same) or FB", "Follow-up A", false, ""],
    ["Apr 18 (Fri)", "Day 3", "5", "Safer Chivas Tour", "Follow-up if no reply", "WhatsApp or IG", "Follow-up A", false, ""],
    ["Apr 19 (Sat)", "Day 3", "3", "Ritual del Kaoz", "Follow-up if no reply", "IG or X @LA48_OFICIAL", "Follow-up A", false, ""],
    ["Apr 19 (Sat)", "Day 3", "4", "La Sangre Azul", "Follow-up if no reply", "IG or X @La_SangreAzul_", "Follow-up A", false, ""],
    // Week 2
    ["Apr 21 (Mon)", "Day 0 prep", "6-10", "All Tier 2", "Verify contact paths, customize scripts", "—", "—", false, ""],
    ["Apr 21 (Mon)", "Day 7", "1", "Barra Insurgencia", "Channel switch if no reply", "Email barralainsurgencia@gmail.com", "Adapted C", false, ""],
    ["Apr 21 (Mon)", "Day 7", "2", "Legion 1908 DF", "Channel switch if no reply", "FB message", "Adapted C", false, ""],
    ["Apr 21 (Mon)", "Day 7", "5", "Safer Chivas Tour", "Channel switch if no reply", "IG DM @safer_chivas_tour", "Adapted C", false, ""],
    ["Apr 22 (Tue)", "Day 0", "6", "La Rebel", "Send initial DM", "IG @rebel_orgulloazulyoro", "A", false, ""],
    ["Apr 22 (Tue)", "Day 0", "7", "Libres y Lokos", "Send initial DM", "IG @weblibresylokos", "C", false, ""],
    ["Apr 22 (Tue)", "Day 0", "8", "La Adiccion", "Send initial DM", "IG @la.adiccion", "C", false, ""],
    ["Apr 22 (Tue)", "Day 7", "3", "Ritual del Kaoz", "Channel switch if no reply", "X DM @LA48_OFICIAL", "Adapted A", false, ""],
    ["Apr 22 (Tue)", "Day 7", "4", "La Sangre Azul", "Channel switch if no reply", "X DM @La_SangreAzul_", "Adapted A", false, ""],
    ["Apr 23 (Wed)", "Day 0", "9", "Furia Azteca", "Send initial DM", "IG @furia_azteca_oficial", "A", false, ""],
    ["Apr 23 (Wed)", "Day 0", "10", "Barra Perra Brava", "Phone call + email", "Phone +52 722 214 0444", "C (verbal)", false, ""],
    ["Apr 25 (Fri)", "Day 3", "6", "La Rebel", "Follow-up if no reply", "IG or X @oficialazulyoro", "Follow-up A", false, ""],
    ["Apr 25 (Fri)", "Day 3", "7", "Libres y Lokos", "Follow-up if no reply", "IG or X @LIBRESYLOKOS", "Follow-up A", false, ""],
    ["Apr 25 (Fri)", "Day 3", "8", "La Adiccion", "Follow-up if no reply", "IG or X @LAADICCIONCFM", "Follow-up A", false, ""],
    ["Apr 25 (Fri)", "Day 10", "1", "Barra Insurgencia", "Warm intro attempt", "Via another contact", "Warm Intro", false, ""],
    ["Apr 25 (Fri)", "Day 10", "2", "Legion 1908 DF", "Warm intro attempt", "Via another contact", "Warm Intro", false, ""],
    // Week 3
    ["Apr 28 (Mon)", "Review", "ALL", "Pipeline Review", "Assess all statuses. If <3 replied, activate backup list.", "—", "—", false, ""],
    ["Apr 29 (Tue)", "Day 14", "1-5", "Tier 1 Final", "Final assessment: archive or keep", "—", "—", false, ""],
    ["Apr 30 (Wed)", "Day 10", "6-8", "La Rebel / LyL / Adiccion", "Warm intro if no reply", "Via contacts or Barras Unidas", "Warm Intro", false, ""],
    // Week 4
    ["May 5 (Mon)", "Pre-test", "TBD", "Test Groups", "Send pre-test checklist (install app, create squad, set meetup)", "WhatsApp", "Checklist", false, ""],
    ["May 7 (Wed)", "Pre-test", "TBD", "Test Groups", "48-hour confirmation", "WhatsApp", "—", false, ""],
    ["May 9-10", "TEST DAY", "TBD", "Test Groups", "Match-day test execution. Be available for real-time support.", "WhatsApp", "—", false, ""],
    ["May 11 (Sun)", "Post-test", "TBD", "Test Groups", "15-min debrief within 24hrs. Document results.", "WhatsApp call", "—", false, ""],
  ];

  schedule.forEach((row, i) => {
    row.forEach((cell, j) => {
      const range = cal.getRange(4 + i, j + 1);
      if (j === 7) {
        range.insertCheckboxes();
        if (cell) range.check();
      } else {
        range.setValue(cell);
      }
    });
  });

  // Column widths
  cal.setColumnWidth(1, 130);
  cal.setColumnWidth(2, 90);
  cal.setColumnWidth(3, 60);
  cal.setColumnWidth(4, 180);
  cal.setColumnWidth(5, 350);
  cal.setColumnWidth(6, 250);
  cal.setColumnWidth(7, 100);
  cal.setColumnWidth(8, 60);
  cal.setColumnWidth(9, 200);

  // Week separators
  const weekStarts = [0, 11, 27, 30]; // Row offsets for week breaks
  weekStarts.forEach(offset => {
    cal.getRange(4 + offset, 1, 1, 9)
      .setBorder(true, null, null, null, null, null, "#e94560", SpreadsheetApp.BorderStyle.SOLID_MEDIUM);
  });
}

/**
 * Utility: Call this to log a status change in the Notes column
 * Usage: logStatusChange(2, "contacted", "IG DM @barra_insurgencia. Script C sent.")
 */
function logStatusChange(leadRow, newStatus, details) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheet = ss.getSheetByName("Leads");

  const today = Utilities.formatDate(new Date(), "America/Mexico_City", "MMM dd");
  const notesCell = sheet.getRange(leadRow, 24); // Column X = Notes
  const existing = notesCell.getValue();
  const entry = `[${today}] ${newStatus}: ${details}`;

  notesCell.setValue(existing ? existing + "\n" + entry : entry);

  // Update status
  sheet.getRange(leadRow, 10).setValue(newStatus);

  // Update last action
  sheet.getRange(leadRow, 22).setValue(today);
  sheet.getRange(leadRow, 23).setValue(details);
}

/**
 * Adds a custom menu to the sheet for quick actions
 */
function onOpen() {
  SpreadsheetApp.getUi().createMenu("Conectx Tracker")
    .addItem("Run Setup", "setupTracker")
    .addItem("Refresh Dashboard", "createDashboard")
    .addSeparator()
    .addItem("Log Status: Contacted", "promptContactedLog")
    .addItem("Log Status: Replied", "promptRepliedLog")
    .addItem("Log Status: Intro Booked", "promptIntroBookedLog")
    .addItem("Log Status: Test Booked", "promptTestBookedLog")
    .addItem("Log Status: Test Completed", "promptTestCompletedLog")
    .addToUi();
}

function promptContactedLog() { promptStatusLog("contacted"); }
function promptRepliedLog() { promptStatusLog("replied"); }
function promptIntroBookedLog() { promptStatusLog("intro booked"); }
function promptTestBookedLog() { promptStatusLog("test booked"); }
function promptTestCompletedLog() { promptStatusLog("test completed"); }

function promptStatusLog(status) {
  const ui = SpreadsheetApp.getUi();
  const row = SpreadsheetApp.getActiveSheet().getActiveCell().getRow();

  if (row < 2) {
    ui.alert("Select a lead row first.");
    return;
  }

  const result = ui.prompt(
    `Log: ${status}`,
    "Enter details (channel, message sent, etc.):",
    ui.ButtonSet.OK_CANCEL
  );

  if (result.getSelectedButton() === ui.Button.OK) {
    logStatusChange(row, status, result.getResponseText());
  }
}
