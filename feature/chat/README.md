# :feature:chat

This module owns the project chat UI. `ChatRoute` obtains a project-scoped `ChatViewModel`,
collects `ChatUiState` with lifecycle awareness, and passes state and actions to `ChatScreen`.
The screen contains a session drawer, a keyed `LazyColumn` of messages, a composer, and the
existing page navigation menu.

## Current local demonstration

The ViewModel keeps sessions and messages in memory. Sending a prompt creates a user message
and streams a clearly labeled sample Markdown reply. This exercises the UI without an Agent.
The sample is not persisted and disappears when the process dies. The existing
`:data:session` repository is still an interface, so persistence is a separate integration step.

## Streaming rendering

`StreamingMarkdown` uses a two-layer crossfade. The initial text is parsed with
`org.intellij.markdown` during composition so its first layout has the correct height. Later
text snapshots are parsed on `Dispatchers.Default`. `snapshotFlow`, `mapLatest`, and
`conflate` discard intermediate tokens when parsing or animation falls behind. Each updated
snapshot first occupies an overlay; its alpha moves from zero to one over 120 ms. After a
frame, it becomes the base and the overlay is removed. `BlendMode.Plus` inside an offscreen
layer avoids a dark midpoint. Only the base has accessibility semantics. Selection is enabled
when generation and the last fade have finished, avoiding selectable registration churn.

The renderer currently covers headings, paragraphs, emphasis, strong text, strike-through,
links, lists, quotes, code blocks, and horizontally scrollable tables. Embedded HTML, media,
math, and syntax coloring require dedicated UI components.

## Scroll following

The list uses a `LazyColumn` with stable message IDs, a bottom spacer, and `snapshotFlow` over
visible items. While the last assistant message streams, it requests the bottom item only when
the list is idle and that spacer is visibly at the bottom. The end-index check prevents an
earlier visible item from being mistaken for the bottom. A user gesture toward
history takes priority while the list scrolls, and leaving the bottom stops automatic requests.
Sending a message does not scroll the list. “Jump to latest” uses
`scrollToItem(totalItemsCount - 1)`. The button waits 500 ms before showing an away-from-bottom
state. Historical Markdown is parsed on its first composition so a lazy item
has its Markdown height immediately when it returns to the viewport. The two-layer fade is
used only for subsequent streaming updates.

## Agent and persistence handoff

When the runtime is ready, replace `streamDemoReply` in `ChatViewModel` with events keyed by
session ID and message ID. Append deltas to the same assistant message, then set `streaming`
to false on completion or error. Keep the same immutable `ChatUiState` shape and stable keys;
`ChatScreen` and `StreamingMarkdown` do not need to know where the stream came from. Observe
session metadata through `SessionRepository` and load message histories on selection.
