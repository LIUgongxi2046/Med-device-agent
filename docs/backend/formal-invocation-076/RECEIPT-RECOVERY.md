# Durable original receipt recovery

The adapter now saves the actual ModelResult and reversible CaptureArtifact as separate immutable, bounded private files. A canonical manifest binds original tenant, actor, invocation, dispatch attempt, receipt ID, approved provider request hash and both file hashes. It is published last using exclusive hard-link publication and file/directory fsync. Existing conflicting files are rejected, never overwritten. Dispatch returns HeldReceipt only after reading and verifying the committed originals.

All processes recovering an attempt must use the same existing private durable directory as DurableDispatchGuard. Directory creation, credentials and permissions are deployment inputs. Files are regular owner-only 0600; the directory is owner-only 0700. Reads reject links, changed files, missing files, malformed/duplicate JSON, schema mismatch, raw body/hash mismatch and a missing or different original dispatch marker.

FormalInvocationAdapter.recover(invocation_id, dispatch_attempt_id) reconstructs the exact canonical IN10 request from these files and submits only `/internal/v1/invocations/{id}/receipts`. It allocates no new receipt/attempt ID and never calls begin-send, credentials or the provider. The original Java/S3 intent and attempt determine write-versus-read-only recovery on the server.

A crash before the originals and manifest are durably published can leave UNKNOWN with retained reservations. The dispatch marker still forbids another provider send. No provider statement retrieval or trusted billing reconciliation producer exists in this slice; missing response bytes cannot be reconstructed. Persistent storage loss is likewise not covered by a same-directory process-restart guarantee.

Evidence: `python-tests/test_formal_receipt_store.py`, 17 actual filesystem/driver tests including a fresh subprocess exact-byte recovery and the actual adapter recovery path. Fixtures were produced by real OpenAI SDK + httpx MockTransport, with no external model call. `python-tests/receipt-results.txt` records the run.
