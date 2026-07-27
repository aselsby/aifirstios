import { AppOperator, createMapsTree, createMessagingTree } from "./app-operator.mjs";

const operator = new AppOperator();

const draft = operator.run({
  packageName: "com.google.android.apps.messaging",
  playbookId: "draftMessage",
  tree: createMessagingTree(),
  input: {
    recipient: "Maya Chen",
    body: "Want to check out Outdoor Jazz At The Garden?"
  }
});

const send = operator.run({
  packageName: "com.google.android.apps.messaging",
  playbookId: "sendMessage",
  tree: draft.tree,
  input: {
    body: "Want to check out Outdoor Jazz At The Garden?"
  }
});

const approvedSend = operator.run({
  packageName: "com.google.android.apps.messaging",
  playbookId: "sendMessage",
  tree: draft.tree,
  input: {
    body: "Want to check out Outdoor Jazz At The Garden?"
  },
  approval: {
    status: "approved",
    exactContent: "Want to check out Outdoor Jazz At The Garden?"
  }
});

const route = operator.run({
  packageName: "com.google.android.apps.maps",
  playbookId: "openRoute",
  tree: createMapsTree(),
  input: {
    destination: "Outdoor Jazz At The Garden"
  }
});

console.log(JSON.stringify({
  draft,
  send,
  approvedSend,
  route,
  audit: operator.audit
}, null, 2));
