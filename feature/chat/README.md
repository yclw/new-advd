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

`StreamingMarkdown` follows Gallery's two-layer crossfade idea. Each new text snapshot is
parsed with `org.intellij.markdown` on `Dispatchers.Default`. `snapshotFlow`, `mapLatest`, and
`conflate` discard intermediate tokens when parsing or animation falls behind. The parsed
snapshot first occupies an overlay; its alpha moves from zero to one over 120 ms. After a
frame, it becomes the base and the overlay is removed. `BlendMode.Plus` inside an offscreen
layer avoids a dark midpoint. Only the base has accessibility semantics. Selection is enabled
when generation and the last fade have finished, avoiding selectable registration churn.

The renderer currently covers headings, paragraphs, emphasis, strong text, strike-through,
links, lists, quotes, code blocks, and horizontally scrollable tables. Embedded HTML, media,
math, and syntax coloring require dedicated UI components.

## Scroll following

The list follows RikkaHub's `LazyColumn` pattern: stable message IDs, a bottom spacer, and
`snapshotFlow` over visible items. While the last assistant message streams, it requests the
bottom item only when the list is idle and that spacer is visibly at the bottom. The end-index
check prevents an earlier visible item from being mistaken for the bottom. If the user scrolls
up, these conditions stop automatic requests; no separate follow flag or size-based scroll
observer can pull against the gesture. Sending a new user message requests the bottom once,
as Gallery does. The “Jump to latest” button uses `scrollToItem(totalItemsCount - 1)` and
Gallery's 500 ms delay before showing an away-from-bottom state.

## Agent and persistence handoff

When the runtime is ready, replace `streamDemoReply` in `ChatViewModel` with events keyed by
session ID and message ID. Append deltas to the same assistant message, then set `streaming`
to false on completion or error. Keep the same immutable `ChatUiState` shape and stable keys;
`ChatScreen` and `StreamingMarkdown` do not need to know where the stream came from. Observe
session metadata through `SessionRepository` and load message histories on selection.
