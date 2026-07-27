import {
  AutonomyMode,
  approveAction,
  createInitialState,
  denyAction,
  runOutdoorIntent,
  setAutonomyMode
} from "../src/conductor-model.mjs";

let state = createInitialState();
let currentTab = "today";

const view = document.querySelector("#view");
const transcript = document.querySelector("#transcript");
const promptInput = document.querySelector("#promptInput");
const runButton = document.querySelector("#runButton");
const modeSelect = document.querySelector("#modeSelect");
const listenButton = document.querySelector("#listenButton");
const tabButtons = Array.from(document.querySelectorAll(".tabs button"));

function render() {
  transcript.textContent = state.transcript;
  modeSelect.value = state.mode;
  tabButtons.forEach((button) => button.classList.toggle("active", button.dataset.tab === currentTab));

  if (currentTab === "today") renderToday();
  if (currentTab === "apps") renderApps();
  if (currentTab === "approvals") renderApprovals();
  if (currentTab === "audit") renderAudit();
}

function renderToday() {
  const task = state.activeTask;
  const cards = [
    card("Task", task ? `${task.utterance}<br><span class=\"badge\">${task.status}</span>` : "No active task."),
    card("Best recommendation", state.recommendations[0] ? recommendationHtml(state.recommendations[0]) : "Run the sample request."),
    `<div class="plan">${state.plan.map(stepHtml).join("") || card("Plan", "The plan appears here before app actions run.")}</div>`
  ];
  view.innerHTML = cards.join("");
}

function renderApps() {
  const apps = state.apps;
  view.innerHTML = [
    card("Calendar", `Events: ${apps.calendar.events.length}. Holds: ${apps.calendar.holds.map((hold) => hold.title).join(", ") || "none"}.`),
    card("Weather", apps.weather.summary),
    card("Facebook Events", apps.events.nearby.map(recommendationHtml).join("<hr>")),
    card("Contacts", apps.contacts.people.map((person) => `${person.name}, free after ${person.freeAfter}`).join("<br>")),
    card("Messages", `Drafts: ${apps.messages.drafts.length}. Sent: ${apps.messages.sent.length}.`),
    card("Maps", `Routes opened: ${apps.maps.routes.map((route) => route.destination).join(", ") || "none"}.`)
  ].join("");
}

function renderApprovals() {
  if (state.approvals.length === 0) {
    view.innerHTML = card("Approval queue", "Nothing is waiting. Sensitive actions will appear here with exact content.");
    return;
  }

  view.innerHTML = state.approvals.map((approval) => `
    <div class="card">
      <div class="row">
        <div>
          <h3>${approval.step.title}</h3>
          <p>${approval.exactContent}</p>
          <p class="small">Destination: ${approval.destination}. ${approval.reason}</p>
        </div>
        <span class="badge warn">Required</span>
      </div>
      <div class="row" style="justify-content:flex-start;margin-top:10px">
        <button class="approve" data-approve="${approval.id}">Approve</button>
        <button class="deny" data-deny="${approval.id}">Deny</button>
      </div>
    </div>
  `).join("");

  view.querySelectorAll("[data-approve]").forEach((button) => {
    button.addEventListener("click", () => {
      state = approveAction(state, button.dataset.approve);
      render();
    });
  });
  view.querySelectorAll("[data-deny]").forEach((button) => {
    button.addEventListener("click", () => {
      state = denyAction(state, button.dataset.deny);
      render();
    });
  });
}

function renderAudit() {
  view.innerHTML = state.audit.slice(0, 30).map((event) => `
    <div class="card">
      <h3>${event.type}</h3>
      <p>${event.detail}</p>
    </div>
  `).join("");
}

function card(title, body) {
  return `<div class="card"><h3>${title}</h3><p>${body}</p></div>`;
}

function recommendationHtml(event) {
  return `<strong>${event.title}</strong><br><span class="small">${event.startsAt}, ${event.distance}, $${event.price}, score ${event.score}</span>`;
}

function stepHtml(step) {
  return `
    <div class="step">
      <div>
        <strong>${step.title}</strong>
        <p class="small">${step.app}: ${step.detail}</p>
      </div>
      <span class="badge ${step.policy.decision === "require_approval" ? "warn" : ""}">${step.policy.decision}</span>
    </div>
  `;
}

function runPrompt(text) {
  state = runOutdoorIntent(state, text);
  currentTab = "today";
  speak(state.transcript);
  render();
}

function speak(text) {
  if (!("speechSynthesis" in window)) return;
  window.speechSynthesis.cancel();
  window.speechSynthesis.speak(new SpeechSynthesisUtterance(text));
}

function startListening() {
  const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!Recognition) {
    runPrompt(promptInput.value);
    return;
  }

  const recognition = new Recognition();
  recognition.lang = "en-US";
  recognition.interimResults = false;
  listenButton.classList.add("listening");
  recognition.onresult = (event) => {
    const text = event.results[0][0].transcript;
    promptInput.value = text;
    runPrompt(text);
  };
  recognition.onend = () => listenButton.classList.remove("listening");
  recognition.start();
}

runButton.addEventListener("click", () => runPrompt(promptInput.value));
listenButton.addEventListener("click", startListening);
modeSelect.addEventListener("change", () => {
  state = setAutonomyMode(state, modeSelect.value ?? AutonomyMode.DRAFT_ONLY);
  render();
});
tabButtons.forEach((button) => {
  button.addEventListener("click", () => {
    currentTab = button.dataset.tab;
    render();
  });
});

render();
