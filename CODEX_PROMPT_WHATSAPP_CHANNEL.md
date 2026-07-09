# CODEX/CLAUDE PROMPT: Implement WhatsApp Channel System in ClawDroid

## Context
You are building a feature into an Android app called "ClawDroid" — an AI agent platform that runs as an Android accessibility service with a sandboxed Linux environment at `/data/data/com.clawdroid.app/files/`. The app already has:
- Android screen control (accessibility service)
- A sandbox with bash, curl, glibc compat layer
- No Python (shell only)
- Existing wacli binary for WhatsApp automation
- A messaging/skill system using markdown config files

## The Problem
1. The terminal/sandbox has no proper terminal UI — `clear` command doesn't work, Ctrl+C can't kill processes
3. There's no proper onboarding flow for connecting WhatsApp
4. No settings UI for managing WhatsApp connection
5. No persistent listening service that survives agent restarts

## What to Build: Full WhatsApp Channel System

### Phase 1: Fix Sandbox Terminal

**Problem**: `clear` doesn't work, Ctrl+C is intercepted by Android and can't stop processes.

**Implementation**:
1. In `/data/data/com.clawdroid.app/files/home/.bashrc` (create if not exists), add:
```bash
# Fix clear command - Android sandbox doesn't support ANSI properly
alias clear='printf "\033[2J\033[H"'
# Or better:
cls() { printf "\033c"; }

# Better prompt
export PS1='\[\033[01;32m\]\u@clawdroid\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\$ '
export TERM=xterm-256color

# Handy aliases
alias ll='ls -la'
alias la='ls -A'
alias l='ls -CF'

# Process management since Ctrl+C doesn't work
alias psall='ps aux | grep -v grep'
# Kill by name: kname process_name
kname() { ps aux | grep -v grep | grep "$1" | awk '{print $2}' | xargs -r kill -9; }
# Find PID: pidof name
pidof() { ps aux | grep -v grep | grep "$1" | awk '{print $2}'; }

# Show running background processes
alias bgjobs='jobs -l'
```

2. When user types `clear` in the ClawDroid terminal, intercept and run `printf "\033c"` instead.
3. When user presses Ctrl+C, show a dialog: "Process not responding to Ctrl+C. Kill it? [Yes] [No]" and if Yes, run `kill -9 PID`.
4. Add a floating "Kill Process" button in the terminal UI when a process is running.
5. Show process list in terminal: `ps aux | grep -v grep` with PIDs visible.

### Phase 2: WhatsApp Onboarding Flow (Channel Connection)

When user taps "Connect Channel → WhatsApp" in ClawDroid:

#### Step 1: Check & Install Dependencies
```bash
# 1. Check if glibc is installed
if ! ls /data/data/com.clawdroid.app/files/usr/glibc/bin/ld.so 2>/dev/null; then
    # Show progress: "Installing glibc compatibility layer..."
    # Run: pkg install glibc
    # Handle: "Unable to locate package" → run "termux-change-repo" first
fi

# 2. Check if wacli binary exists
if [ ! -f /data/data/com.clawdroid.app/files/wacli ]; then
    # Show progress: "Downloading WhatsApp bridge (wacli)..."
    # Fetch latest release URL:
    WACLI_URL=$(curl -s https://api.github.com/repos/itsToggle/wacli/releases/latest \
      | grep "browser_download_url" | grep "linux_arm64" | head -1 | cut -d'"' -f4)
    curl -L -o /data/data/com.clawdroid.app/files/wacli "$WACLI_URL"
    chmod +x /data/data/com.clawdroid.app/files/wacli
fi
```

#### Step 2: Show Phone Number Input Screen
```
┌─────────────────────────────────────────────┐
│  📱 Connect WhatsApp                         │
├─────────────────────────────────────────────┤
│                                             │
│  Enter your WhatsApp number with country    │
│  code:                                      │
│                                             │
│  ┌─────────────────────────────────┐        │
│  │ +91 70701 40150                 │        │
│  └─────────────────────────────────┘        │
│                                             │
│  [  Cancel  ]    [  Connect  ]              │
│                                             │
└─────────────────────────────────────────────┘
```

#### Step 3: Generate & Display Pairing Code
```bash
W="env LD_LIBRARY_PATH=/data/data/com.clawdroid.app/files/usr/glibc/lib \
  /data/data/com.clawdroid.app/files/usr/glibc/bin/ld.so \
  /data/data/com.clawdroid.app/files/wacli"

# Run auth with phone number
$W auth --phone "+919876543210" 2>&1 | tee /tmp/wacli_auth.log &

# Extract pairing code from output
# wacli outputs: "Pairing code: ABCD-1234" or "PairingCode: ABCD-1234"
CODE=$(grep -oE '[A-Z0-9]{4}-[A-Z0-9]{4}' /tmp/wacli_auth.log | head -1)
```

#### Step 4: Display Pairing Instructions Screen
```
┌─────────────────────────────────────────────┐
│  🔗 Link Your WhatsApp                       │
├─────────────────────────────────────────────┤
│                                             │
│  Your pairing code:                         │
│                                             │
│      ╔═══════════════════════╗              │
│      ║    ABCD-1234          ║              │
│      ╚═══════════════════════╝              │
│                                             │
│  Follow these steps:                        │
│                                             │
│  1️⃣ Open WhatsApp on your phone             │
│                                             │
│  2️⃣ Go to Settings → Linked Devices         │
│     (or three-dot menu → Linked Devices)    │
│                                             │
│  3️⃣ Tap "Link a Device"                     │
│                                             │
│  4️⃣ 👇 IMPORTANT 👇                         │
│     Below the QR scanner, tap               │
│     "Link with phone number instead"        │
│                                             │
│  5️⃣ Enter this code:  ABCD-1234             │
│                                             │
│  ⏳ Waiting for you to enter the code...     │
│  [Cancel]                                    │
│                                             │
└─────────────────────────────────────────────┘
```

#### Step 5: Complete & Verify
```bash
# Wait for auth to complete
wait $AUTH_PID 2>/dev/null

# Add account
$W accounts add personal
$W accounts default personal

# Verify
$W accounts list
# Should show: * personal
```

#### Step 6: Show Success Screen
```
┌─────────────────────────────────────────────┐
│  ✅ WhatsApp Connected!                      │
├─────────────────────────────────────────────┤
│                                             │
│  🟢 Connected as: +91 70701 40150           │
│                                             │
│  What's next?                               │
│                                             │
│  📋 Select chats to monitor                  │
│  ⚙️ Configure auto-reply settings            │
│  🚀 Start listening now                      │
│                                             │
│  [Go to Settings]    [Start Listening]      │
│                                             │
└─────────────────────────────────────────────┘
```

### Phase 3: Settings UI for WhatsApp

Create a settings page accessible from ClawDroid main menu → Settings → WhatsApp Channel (or directly after connecting).

#### Settings Sections:

**3.1 Connection**
- Status indicator: 🟢 Connected / 🔴 Disconnected / 🟡 Reconnecting
- Phone number display (read-only)
- [Disconnect] button — runs cleanup, deletes session
- [Reconnect] button — re-runs auth
- [Force Refresh] button — restarts sync daemon

**3.2 Message Policies** (checkboxes with descriptions)
```
☐ Auto-reply to new messages
  When enabled, the agent will automatically reply to new messages
  from monitored chats. When disabled, messages are queued for manual review.

☐ Notify agent on new messages
  Send a notification to the agent when a new message is detected.

☐ Auto-download media
  Automatically download images, videos, and documents sent in chats.

☐ Send read receipts
  Mark messages as read when the agent reads them. Disable for stealth mode.

☐ Show typing indicator
  Show "typing..." when the agent is generating a reply.
```

**3.3 Reply Behavior**
```
Reply delay: [0s] [1s] [3s] [5s] [10s] [Random]
Agent reply mode:
  ○ Smart context-aware (agent generates reply)
  ○ Manual (queue for user to reply)
  ○ Ask me each time
  ○ Template-based (select from saved templates)

Reply language: [English] [Hindi] [Hinglish] [Auto-detect]

Custom reply prompt:
  ┌─────────────────────────────────────────┐
  │ You are a helpful assistant. Reply in   │
  │ a friendly tone. Keep responses under   │
  │ 200 characters unless needed...         │
  └─────────────────────────────────────────┘
```

**3.4 Monitored Chats**
```
List of groups/contacts with toggle switches:
┌─────────────────────────────────────────────┐
│  Synced Chats (last sync: 2 min ago)        │
│                                             │
│  ● Polymaths                     [Active]   │
│  ● Asura Goa Chapter X            [Paused]  │
│  ○ Personal Chat                  [Off]     │
│  ○ +91 98765 43210                [Off]     │
│                                             │
│  [+ Add Chat by JID]  [Refresh List]        │
│                                             │
│  Filter: [All] [Active] [Paused] [Off]      │
└─────────────────────────────────────────────┘
```
Tapping a chat opens per-chat settings:
```
┌─────────────────────────────────────────────┐
│  Chat: Polymaths                             │
├─────────────────────────────────────────────┤
│                                             │
│  Monitoring: [ON]                           │
│  Auto-reply: [ON]                           │
│  Notify on message: [ON]                    │
│  Download media: [OFF]                      │
│                                             │
│  Last 10 messages:                          │
│  ├ Nabil: Hey                                │
│  ├ Paris: What's up                         │
│  ├ Nova: I'm an agent                       │
│  └ ...                                      │
│                                             │
│  [Clear Chat History] [Export Chat]         │
│                                             │
└─────────────────────────────────────────────┘
```

**3.5 Data & Storage**
```
Messages stored: 5,234
Database size: 12.4 MB
Sync status: 🟢 Live (last sync: 2 seconds ago)

Max messages to store:
  ○ 500 (lightweight)
  ○ 2000 (recommended)  
  ○ 10000 (heavy)
  ○ Unlimited

[Force Full Resync] — deletes DB, re-syncs from server
[Clear Local Cache] — removes media downloads, keeps messages
[Export All Chats] — exports as JSON to shared storage
[Delete All Data] — removes session, logs out
```

**3.6 Listening Services**
```
Background Listener: 🟢 Active
Webhook URL: http://localhost:8765/
Webhook Port: [8765] (editable)

Listener status:
  - wacli sync (WebSocket): 🟢 Connected
  - Webhook HTTP server: 🟢 Listening
  - Message daemon: 🟢 Running (PID: 12345)

[Restart Listener] [Stop Listener] [View Logs]

Auto-start on app launch: [ON]
```

### Phase 4: Listening Service Implementation

The core background system that runs 24/7:

#### 4.1 Architecture
```
┌───────────────────────────────────────────────┐
│             CLAWDROID APP UI                   │
│  (Settings, Onboarding, Chat List)             │
└──────────────────┬────────────────────────────┘
                   │ starts/stops
┌──────────────────▼────────────────────────────┐
│         AGENT (Nova / Claude / etc)            │
│  - Reads pending queue                          │
│  - Generates contextual replies                 │
│  - Manages settings                            │
└──────────────────┬────────────────────────────┘
                   │ triggers
┌──────────────────▼────────────────────────────┐
│         LISTENER DAEMON (native-skill)          │
│  - 3 services in 1 process                      │
│    1. wacli sync --follow (WebSocket)          │
│    2. DB poller (checks new messages)          │
│    3. Settings watcher (reloads on change)     │
│  - Queues to pending_replies/ directory        │
│  - Sends Android notification on new msg       │
│  - Auto-restarts on crash (up to 3 times)      │
└──────────────────┬────────────────────────────┘
                   │ reads/writes
┌──────────────────▼────────────────────────────┐
│         STOPAGE / DATA                         │
│  - SQLite DB (wacli.db)                        │
│  - Queue files (pending_replies/*.msg)         │
│  - Config (settings.json)                      │
│  - Logs (native-skill-events.log)              │
└────────────────────────────────────────────────┘
```

#### 4.2 Listener Daemon Code (daemon.sh)

```bash
#!/system/bin/sh
# WhatsApp Listener Daemon
# Auto-started by ClawDroid on boot or when channel is activated

# === CONFIG ===
BASE="/data/data/com.clawdroid.app/files"
W="env LD_LIBRARY_PATH=$BASE/usr/glibc/lib $BASE/usr/glibc/bin/ld.so $BASE/wacli"
G="120363427319534657@g.us"  # Default group, from config
PID_FILE="$BASE/tmp/listener.pid"
EVENTS_LOG="$BASE/tmp/native-skill-events.log"
CONFIG="$BASE/home/.native-skill/settings.json"
QUEUE_DIR="$BASE/tmp/pending_replies"
PROCESSED_FILE="$BASE/tmp/dae_processed_ids.txt"
HEARTBEAT_FILE="$BASE/tmp/listener_heartbeat.txt"

echo "$$" > "$PID_FILE"

# === LOAD SETTINGS ===
load_settings() {
    if [ -f "$CONFIG" ]; then
        AUTO_REPLY=$(grep -o '"auto_reply":[^,}]*' "$CONFIG" | cut -d':' -f2 | tr -d ' ')
        NOTIFY=$(grep -o '"notify_on_message":[^,}]*' "$CONFIG" | cut -d':' -f2 | tr -d ' ')
        MAX_MSGS=$(grep -o '"max_stored_messages":[^,}]*' "$CONFIG" | cut -d':' -f2 | tr -d ' ')
        DELAY=$(grep -o '"reply_delay_seconds":[^,}]*' "$CONFIG" | cut -d':' -f2 | tr -d ' ')
    else
        # Defaults
        AUTO_REPLY="true"
        NOTIFY="true"
        MAX_MSGS="5000"
        DELAY="2"
    fi
}

# === SEND NOTIFICATION TO AGENT ===
notify_agent() {
    local SENDER="$1" TEXT="$2" CHAT="$3"
    am broadcast -a com.clawdroid.NOTIFY \
      --es title "💬 $SENDER" \
      --es body "$TEXT" \
      --es chat "$CHAT" \
      --ez alert true 2>/dev/null || true
    
    # Also write to alert file for agent to poll
    echo "{\"sender\":\"$SENDER\",\"text\":\"$TEXT\",\"chat\":\"$CHAT\",\"time\":\"$(date -Iseconds)\"}" \
      >> "$BASE/tmp/new_msg_alerts.json"
    # Keep only last 10 alerts
    tail -10 "$BASE/tmp/new_msg_alerts.json" > "$BASE/tmp/new_msg_alerts.tmp" \
      && mv "$BASE/tmp/new_msg_alerts.tmp" "$BASE/tmp/new_msg_alerts.json"
}

# === QUEUE MESSAGE FOR AGENT ===
queue_message() {
    local SENDER="$1" TEXT="$2" CHAT="$3"
    mkdir -p "$QUEUE_DIR"
    local MSG_FILE="$QUEUE_DIR/$(date +%s)_${SENDER}.msg"
    cat > "$MSG_FILE" << EOF
FROM=$SENDER
GROUP=$CHAT
TEXT=$TEXT
TIME=$(date '+%Y-%m-%d %H:%M:%S')
EOF
    echo "[$(date)] 📩 Queued from $SENDER in $CHAT: $TEXT" >> "$EVENTS_LOG"
}

# === MAIN LOOP ===
load_settings
echo "[$(date)] === WhatsApp Listener Daemon Started (PID: $$) ===" >> "$EVENTS_LOG"
touch "$PROCESSED_FILE"

# Update heartbeat every 30s to prove we're alive
(
    while true; do
        date +%s > "$HEARTBEAT_FILE"
        sleep 30
    done
) &
HEARTBEAT_PID=$!

while true; do
    # Reload settings periodically
    load_settings
    
    # Get last 5 messages from monitored chats
    $W messages list --chat "$G" --limit 5 --json --read-only > "$BASE/tmp/daemon_raw.json" 2>/dev/null
    
    if [ -s "$BASE/tmp/daemon_raw.json" ] && grep -q '"messages"' "$BASE/tmp/daemon_raw.json" 2>/dev/null; then
        # Process each message
        awk '{printf "%s", $0}' "$BASE/tmp/daemon_raw.json" | tr '}' '\n' | grep "MsgID" | while read -r block; do
            ID=$(echo "$block" | grep -o '"MsgID":"[^"]*"' | head -1 | cut -d'"' -f4)
            SN=$(echo "$block" | grep -o '"SenderName":"[^"]*"' | head -1 | cut -d'"' -f4)
            FM=$(echo "$block" | grep -o '"FromMe":[^,}]*' | head -1 | cut -d':' -f2 | tr -d ' ,}]')
            TX=$(echo "$block" | grep -o '"Text":"[^"]*"' | head -1 | cut -d'"' -f4)
            CJ=$(echo "$block" | grep -o '"ChatJID":"[^"]*"' | head -1 | cut -d'"' -f4)
            
            [ -z "$ID" ] && continue
            grep -q "^$ID\$" "$PROCESSED_FILE" 2>/dev/null && continue
            echo "$ID" >> "$PROCESSED_FILE"
            
            if [ "$FM" = "false" ] && [ -n "$SN" ]; then
                if [ "$NOTIFY" = "true" ]; then
                    notify_agent "$SN" "$TX" "$CJ"
                fi
                if [ "$AUTO_REPLY" = "true" ]; then
                    queue_message "$SN" "$TX" "$CJ"
                fi
            fi
        done
    fi
    
    sleep 3
done
```

#### 4.3 Startup Script (startup.sh)
```bash
#!/system/bin/sh
# Called by ClawDroid on app launch or when "Start Listening" is tapped

BASE="/data/data/com.clawdroid.app/files"
W="env LD_LIBRARY_PATH=$BASE/usr/glibc/lib $BASE/usr/glibc/bin/ld.so $BASE/wacli"
E="$BASE/tmp/native-skill-events.log"

echo "[$(date)] === Starting WhatsApp Listener Stack ===" >> "$E"

# 1. Start wacli sync (WebSocket connection)
if ! ps aux | grep -q "[w]acli sync"; then
    nohup $W sync --follow > "$BASE/tmp/wacli-sync.log" 2>&1 &
    echo "[$(date)] ✅ wacli sync started (PID: $!)" >> "$E"
    sleep 5  # Give it time to connect
else
    echo "[$(date)] ✅ wacli sync already running" >> "$E"
fi

# 2. Start listener daemon
if ! ps aux | grep -q "[l]istener_daemon.sh"; then
    nohup sh "$BASE/home/.native-skill/listener_daemon.sh" > "$BASE/tmp/listener-daemon.log" 2>&1 &
    echo "[$(date)] ✅ Listener daemon started (PID: $!)" >> "$E"
else
    echo "[$(date)] ✅ Listener daemon already running" >> "$E"
fi

# 3. Verify
sleep 2
SYNC_PID=$(ps aux | grep "[w]acli sync" | awk '{print $2}')
DAEMON_PID=$(cat "$BASE/tmp/listener.pid" 2>/dev/null)
echo "[$(date)] === Stack ready: sync=$SYNC_PID daemon=$DAEMON_PID ===" >> "$E"
```

#### 4.4 Settings Config File (settings.json)
```json
{
  "whatsapp": {
    "enabled": true,
    "phone": "+919876543210",
    "auto_reply": true,
    "notify_on_message": true,
    "auto_download_media": false,
    "send_read_receipts": false,
    "show_typing_indicator": false,
    "reply_delay_seconds": 2,
    "reply_mode": "smart",
    "reply_language": "english",
    "custom_reply_prompt": "",
    "monitored_chats": [
      {
        "jid": "120363427319534657@g.us",
        "name": "Polymaths",
        "active": true,
        "auto_reply": true,
        "notify": true
      }
    ],
    "max_stored_messages": 5000,
    "auto_sync": true,
    "sync_interval_seconds": 60,
    "listener_port": 8765,
    "webhook_enabled": false,
    "auto_start_on_launch": true
  }
}
```

### Phase 5: Agent Integration (SKILL.md)

The agent should be able to:
1. Read the pending queue: `ls /data/data/com.clawdroid.app/files/tmp/pending_replies/*.msg`
2. Read a queued message:
```bash
cat /data/data/com.clawdroid.app/files/tmp/pending_replies/12345_Nabil!.msg
# Output:
# FROM=Nabil!
# GROUP=120363427319534657@g.us
# TEXT=Hey what's up?
# TIME=2026-06-16 16:00:00
```
3. Send a reply via wacli:
```bash
W="env LD_LIBRARY_PATH=/data/data/com.clawdroid.app/files/usr/glibc/lib /data/data/com.clawdroid.app/files/usr/glibc/bin/ld.so /data/data/com.clawdroid.app/files/wacli"
$W send text --to "120363427319534657@g.us" --message "Hey! What's up?"
```
4. Move processed message to archive:
```bash
mv /data/data/com.clawdroid.app/files/tmp/pending_replies/*.msg /data/data/com.clawdroid.app/files/tmp/processed_replies/
```

### Phase 6: Android UI Screens

#### 6.1 Onboarding Screen 1: Welcome
```
┌─────────────────────────────────────────────┐
│  🔌 Connect WhatsApp Channel                 │
├─────────────────────────────────────────────┤
│                                             │
│  🟢 WhatsApp                                │
│                                             │
│  Connect your WhatsApp to let your AI       │
│  agent read and reply to messages.          │
│                                             │
│  What you'll need:                          │
│  • Your phone number (with country code)    │
│  • Access to your phone for 30 seconds      │
│                                             │
│  [Back]              [Connect →]            │
│                                             │
└─────────────────────────────────────────────┘
```

#### 6.2 Onboarding Screen 2: Phone Input
```
┌─────────────────────────────────────────────┐
│  📱 Enter Your Phone Number                  │
├─────────────────────────────────────────────┤
│                                             │
│  Country code will be auto-detected.        │
│                                             │
│  ┌─────────────────────────────────┐        │
│  │ +91 | 70701 40150               │        │
│  └─────────────────────────────────┘        │
│                                             │
│  [Back]          [Generate Code]            │
│                                             │
└─────────────────────────────────────────────┘
```

#### 6.3 Onboarding Screen 3: Pairing Code
```
┌─────────────────────────────────────────────┐
│  🔗 Link Your WhatsApp                       │
├─────────────────────────────────────────────┤
│                                             │
│  Pairing code generated!                     │
│                                             │
│      ╔═══════════════════════╗              │
│      ║    ABCD-1234          ║              │
│      ╚═══════════════════════╝              │
│         [Tap to copy to clipboard]          │
│                                             │
│  📋 Steps:                                  │
│                                             │
│  1️⃣ Open WhatsApp on your phone             │
│  2️⃣ Settings → Linked Devices               │
│  3️⃣ Tap "Link a Device"                     │
│  4️⃣ Tap "Link with phone number instead"    │
│     (below the QR code scanner)             │
│  5️⃣ Enter this code                         │
│                                             │
│  ⏳ Waiting for you to enter the code...     │
│                                             │
│  [Cancel]                                   │
│                                             │
└─────────────────────────────────────────────┘
```

#### 6.4 Settings Page
```
┌─────────────────────────────────────────────┐
│  ⚙️ WhatsApp Channel Settings                │
├─────────────────────────────────────────────┤
│                                             │
│  📱 CONNECTION                              │
│  ─────────────────────────────────          │
│  Status ● Connected                         │
│  Phone: +91 70701 40150                     │
│  [Disconnect] [Reconnect]                   │
│                                             │
│  💬 MESSAGE POLICIES                        │
│  ─────────────────────────────────          │
│  ☑ Auto-reply to new messages               │
│  ☑ Notify agent on new messages             │
│  ☐ Auto-download media                      │
│  ☐ Send read receipts                       │
│  ☐ Show typing indicator                    │
│                                             │
│  🤖 REPLY BEHAVIOR                          │
│  ─────────────────────────────────          │
│  Delay: ○ 0s ○ 1s ○ 3s ● 5s ○ 10s          │
│  Mode: ● Smart ○ Manual ○ Ask ○ Template   │
│  Language: [English ▼]                      │
│                                             │
│  👥 MONITORED CHATS                         │
│  ─────────────────────────────────          │
│  ● Polymaths [Edit]                         │
│  ○ Asura Bug Busters [Edit]                 │
│  ○ +91 98765 43210 [Edit]                   │
│  [+ Add Chat]                               │
│                                             │
│  📊 DATA & STORAGE                          │
│  ─────────────────────────────────          │
│  Messages: 5,234 | DB: 12.4 MB              │
│  Max: [5000 ▼]                              │
│  [Clear Cache] [Export] [Force Resync]     │
│                                             │
│  🎯 LISTENER                                │
│  ─────────────────────────────────          │
│  Status: ● Running (PID: 12345)             │
│  Auto-start: [ON]                           │
│  [Stop] [Restart] [View Logs]               │
│                                             │
└─────────────────────────────────────────────┘
```

### Phase 7: Implementation Code Snippets

#### 7.1 Java/Kotlin: Launch wacli sync from Android
```kotlin
// In ClawDroid WhatsAppChannelService.kt
class WhatsAppChannelService : Service() {
    
    private fun startWacliSync() {
        val env = mapOf(
            "LD_LIBRARY_PATH" to "$filesDir/usr/glibc/lib",
            "WACLI_STORE_DIR" to "$filesDir/wacli-data"
        )
        val cmd = arrayOf(
            "$filesDir/usr/glibc/bin/ld.so",
            "$filesDir/wacli",
            "sync", "--follow"
        )
        val pb = ProcessBuilder(cmd)
            .environment().apply {
                env.forEach { (k, v) -> put(k, v) }
            }
            .directory(File(filesDir))
            .redirectErrorStream(true)
        
        process = pb.start()
        // Read output in background thread
        thread {
            process?.inputStream?.bufferedReader()?.use { reader ->
                reader.lines().forEach { line ->
                    Log.d("WacliSync", line)
                    // Update UI with connection status
                }
            }
        }
    }
    
    private fun checkAuthStatus(): Boolean {
        val pb = ProcessBuilder(
            "$filesDir/usr/glibc/bin/ld.so",
            "$filesDir/wacli",
            "accounts", "list"
        )
        pb.environment().put("LD_LIBRARY_PATH", "$filesDir/usr/glibc/lib")
        val proc = pb.start()
        val output = proc.inputStream.bufferedReader().readText()
        return output.contains("personal")
    }
}
```

#### 7.2 Kotlin: Onboarding UI
```kotlin
@Composable
fun PhoneInputScreen(onNext: (String) -> Unit) {
    var phone by remember { mutableStateOf("+91") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Phone,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Enter Your WhatsApp Number", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone number") },
            placeholder = { Text("+91 70701 40150") },
            leadingIcon = { Icon(Icons.Default.Phone, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { onNext(phone) },
            enabled = phone.length >= 10,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Generate Pairing Code", fontSize = 18.sp)
        }
    }
}

@Composable
fun PairingCodeScreen(code: String, onCancel: () -> Unit) {
    var waiting by remember { mutableStateOf(true) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Code display
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Your Pairing Code", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    code,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { /* copy to clipboard */ }) {
                    Icon(Icons.Default.ContentCopy, null)
                    Text("Tap to copy")
                }
            }
        }
        
        // Instructions
        InstructionStep(1, "Open WhatsApp on your phone")
        InstructionStep(2, "Go to Settings → Linked Devices")
        InstructionStep(3, "Tap Link a Device")
        InstructionStep(4, "Tap 'Link with phone number instead'", highlight = true)
        InstructionStep(5, "Enter: $code")
        
        Spacer(modifier = Modifier.weight(1f))
        
        if (waiting) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Waiting for you to enter the code...")
            }
        }
        
        OutlinedButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}
```

#### 7.3 Shell: Full Onboarding Script
```bash
#!/system/bin/sh
# WhatsApp Channel Onboarding - Full Script
# To be called from ClawDroid app

BASE="/data/data/com.clawdroid.app/files"
LOG="$BASE/tmp/onboarding.log"
W="env LD_LIBRARY_PATH=$BASE/usr/glibc/lib $BASE/usr/glibc/bin/ld.so $BASE/wacli"

echo "[$(date)] Starting WhatsApp onboarding..." > "$LOG"

# === STEP 1: Install Dependencies ===
echo "📦 Installing dependencies..." >> "$LOG"
pkg install -y glibc 2>> "$LOG" || {
    # Handle missing repo
    echo "⚠️ termux-change-repo may be needed" >> "$LOG"
    termux-change-repo 2>/dev/null
    pkg install -y glibc 2>> "$LOG"
}

# === STEP 2: Download wacli ===
if [ ! -f "$BASE/wacli" ]; then
    echo "📥 Downloading wacli..." >> "$LOG"
    WACLI_URL=$(curl -s https://api.github.com/repos/itsToggle/wacli/releases/latest \
      | grep "browser_download_url" | grep "linux_arm64" | head -1 | cut -d'"' -f4)
    
    if [ -z "$WACLI_URL" ]; then
        echo "❌ Failed to get wacli download URL" >> "$LOG"
        exit 1
    fi
    
    curl -L -o "$BASE/wacli" "$WACLI_URL" 2>> "$LOG"
    chmod +x "$BASE/wacli"
    echo "✅ wacli downloaded" >> "$LOG"
else
    echo "✅ wacli already exists" >> "$LOG"
fi

# === STEP 3: Phone Auth ===
echo "📱 Phone number received from UI" >> "$LOG"
PHONE="$1"  # Passed from UI as argument

# Run auth
echo "🔑 Running auth for $PHONE..." >> "$LOG"
$W auth --phone "$PHONE" 2>&1 | tee -a "$LOG" &
AUTH_PID=$!
sleep 4

# Extract pairing code
CODE=$(grep -oE '[A-Z0-9]{4}-[A-Z0-9]{4}' "$LOG" | head -1)
echo "CODE=$CODE" >> "$LOG"

# === STEP 4: Wait for completion ===
# The UI will show the code and instructions
# When user enters code on phone, auth completes

wait $AUTH_PID 2>/dev/null
AUTH_EXIT=$?

if [ $AUTH_EXIT -eq 0 ]; then
    echo "✅ Auth successful!" >> "$LOG"
    
    # === STEP 5: Setup Account ===
    $W accounts add personal 2>> "$LOG"
    $W accounts default personal 2>> "$LOG"
    
    # Verify
    $W accounts list 2>> "$LOG"
    
    echo "✅ WhatsApp Channel connected!" >> "$LOG"
    echo "RESULT=success" >> "$LOG"
else
    echo "❌ Auth failed (exit: $AUTH_EXIT)" >> "$LOG"
    echo "RESULT=failed" >> "$LOG"
fi
```

## Summary of Files to Create/Modify

| File | Purpose |
|------|---------|
| `app/src/main/java/com/clawdroid/app/channel/WhatsAppChannelService.kt` | Background service for wacli |
| `app/src/main/java/com/clawdroid/app/channel/WhatsAppSettingsScreen.kt` | Settings UI (Jetpack Compose) |
| `app/src/main/java/com/clawdroid/app/channel/OnboardingScreens.kt` | Pairing flow UI |
| `app/src/main/java/com/clawdroid/app/channel/ChatListScreen.kt` | Chat monitoring list |
| `app/src/main/res/layout/terminal_fixes.xml` | Terminal UI enhancements |
| `files/home/.native-skill/listener_daemon.sh` | Background message watcher |
| `files/home/.native-skill/startup.sh` | Auto-start on boot |
| `files/home/.native-skill/settings.json` | User settings config |
| `files/home/.bashrc` | Terminal improvements |
| `files/AGENTS.md` | Agent memory for WhatsApp |
| `files/SKILL.md` | Agent skill definition |

## Testing Checklist
- [ ] `clear` command now works in terminal
- [ ] Ctrl+C shows kill dialog or `kill -2 PID` works
- [ ] Onboarding flow completes: phone → code → enter on phone → verified
- [ ] Settings page loads with current connection status
- [ ] Toggle auto-reply on/off works
- [ ] Adding/removing monitored chats works
- [ ] Listener daemon detects new messages within 5 seconds
- [ ] Agent receives notification when new message arrives
- [ ] Agent can reply via wacli command
- [ ] After app restart, listener auto-starts
- [ ] Disconnect button properly cleans up session
- [ ] Error states show properly (no internet, auth expired, rate limited)
- [ ] DB doesn't grow unbounded (max_messages setting respected)
- [ ] Multiple chats can be monitored simultaneously

## End of Prompt
