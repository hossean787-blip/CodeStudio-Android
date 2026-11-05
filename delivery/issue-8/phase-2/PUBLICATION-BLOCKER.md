# Automatic project publication is blocked; never exposed as complete

The newly proposed exclusive writer was NOT merged to main. Review identified
that canonical checks plus path-based staging/link can be redirected by a
concurrent parent swap. Descriptor anchoring alone also does not establish
ancestry if the original parent/root itself is moved across project boundaries.
The isolated reviewer reproduced the latter with host OS directory descriptors.

Ruling: retain strict project-isolation semantics rather than narrow the promise.
Revert the unverified FileFs capability, tool registration, engine hooks and undo
integration. A regression test ensures automatic create_document is not advertised
for normal workspaces. No existing healthy production feature is removed; this
was an unmerged new increment. Four earlier host filesystem test passes covered
static cases and are superseded by the security finding, not acceptance evidence.

Validated native encoders remain useful: connect them to an explicit Android
CreateDocument user-choice export flow, outside automatic project writes. The
user chooses destination; do not imply an arbitrary provider guarantees atomic
commit. Verify saved bytes and show errors honestly. Automatic model-to-project
publication remains blocked pending an architecture that enforces mutation/
namespace boundaries for all concurrent actors. This is NOT completion of R09,
R20, R21/R22 isolation or all26 requirements.
