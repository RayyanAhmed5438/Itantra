Sure! Here's a simple explanation of each of the 11 modules in the app, using everyday analogies. Think of the app as a **special walkie-talkie system** that also sends emergency alerts and watches your back.

---

### 1. `core-domain` — **The Shared Language / Rulebook**

This is where we define all the basic **words and concepts** the whole app uses. It’s like a dictionary and a set of rules that everyone agrees on. For example:

- What is a “device” or “peer”?
- What is a “text message packet”?
- What is an “emergency alert”?
- What is a “language tag”?

No actual work happens here—just definitions. Everything else in the app refers to these definitions so they all speak the same language.

---

### 2. `core-platform-api` — **The List of Abilities (Contracts)**

This module is a **list of things the phone should be able to do**, without saying *how* to do them. For example:

- “Record audio from the microphone”
- “Play audio through the speaker”
- “Send and receive data over Bluetooth or Wi‑Fi”
- “Vibrate the phone”
- “Turn on the flashlight”
- “Recognize speech and turn it into text”
- “Turn text into spoken audio”

Think of it as a **job description** for the phone’s hardware and speech software. It says *what* must be possible, but not *how* it’s done. The actual “how” is handled by the next module.

---

### 3. `core-protocol` — **The Envelope and Postage System**

When two phones talk to each other, they need to send information in a **standard format**. This module defines:

- How to pack a message into a **neat envelope** (with a header, checksum, etc.).
- How to check if the envelope is **tampered or corrupted**.
- How to give each message a **unique fingerprint** so it doesn’t get delivered twice.

It’s like the **postal service rules**: how to address, seal, and verify a letter, so it arrives safely and only once.

---

### 4. `platform-android` — **The Actual Hands and Tools**

This is the only module that actually **touches the phone’s hardware and Android system**. It takes the job descriptions from `core-platform-api` and **does the real work**:

- Uses the microphone to record sound.
- Uses the speaker to play sound.
- Uses Bluetooth and Wi‑Fi to send/receive data.
- Controls the flashlight, vibration, volume, Do‑Not‑Disturb, etc.
- Loads and runs the **speech recognition (STT) and text‑to‑speech (TTS) models**.

Importantly, this module also keeps the speech models **swappable**—like being able to change the engine of a car without rebuilding the whole car. If we want a better speech model later, we only touch this part.

---

### 5. `engine-discovery` — **The Radar**

This module answers: **“Who is near me right now?”**

It constantly scans for other phones using Bluetooth and Wi‑Fi Direct, figures out how far away they are (using signal strength), and keeps a live list of nearby squad members. It also sends out a **heartbeat** every few seconds so others can see you.

It’s like a **military radar** that shows all friendly units in the area.

---

### 6. `engine-mesh` — **The Message Router**

When you send a message, it might need to hop through several phones to reach someone far away. This module handles **how messages travel** across the squad:

- It receives messages from other phones.
- It checks if the message is new (not a duplicate).
- It forwards the message to other phones to extend the range.
- It makes sure a message doesn’t bounce around forever (uses a “hop count” limit).

Think of it as the **postal sorting system** for the walkie‑talkie network—getting your message from A to B even if there’s no direct line.

---

### 7. `engine-speech` — **The Language Brain (Speech↔Text)**

This is the module that turns **spoken words into text** (speech‑to‑text, STT) and **text back into speech** (text‑to‑speech, TTS).

- **When you talk:** It listens to your voice, detects when you pause, and converts what you said into text **in your own language**.
- **When someone else talks:** Their text arrives on your phone, and this module reads it out loud **in that same language** (so you hear it in the language they spoke).

**Important:** There is **no translation**—if someone speaks Hindi, you hear Hindi. The app never changes the language. This keeps things fast and simple.

---

### 8. `feature-ptt` — **The Walkie‑Talkie Button**

This is the **push‑to‑talk (PTT)** and **voice‑activated (VOX)** experience.

- **PTT mode:** You press and hold a button to talk; release to stop. It’s like a traditional walkie‑talkie.
- **VOX mode:** The phone automatically starts recording when you speak and stops when you’re silent—like a phone call.

This module manages the **state machine** (idle → recording → transmitting → back to idle) and sends your voice (as text) over the mesh. It also gives little vibrations as feedback so you know when it’s recording.

---

### 9. `feature-emergency` — **The Panic Button & Siren**

This module handles **life‑safety emergencies**:

- On your phone: You can trigger an emergency (like a 2‑second hold on a panic button). The phone sends an alert with your location and a description to the whole squad—maximum priority.
- On everyone else’s phone: When they receive an emergency, it **forces the phone to max volume, bypasses Do‑Not‑Disturb, flashes the flashlight, and speaks the alert out loud** so nobody misses it.

This is the one case where raw audio might be sent instead of text, to make sure the alarm is heard even if the text fails.

---

### 10. `feature-sentry` — **The Perimeter Tripwire**

This module turns your phone into a **passive guard**. When you “arm” sentry mode, the phone goes into a **low‑power listening mode**—it scans for a specific emergency signal (like a special code) using Bluetooth, but only a tiny fraction of the time (<1% duty cycle) to save battery.

If it detects that signal (from another phone in distress), it **wakes up the phone, sounds the alarm, flashes the light, and speaks a warning**—even if the app wasn’t open.

It’s like leaving a guard dog that sleeps lightly and only barks when an intruder steps on a tripwire.

---

### 11. `app` — **The Control Panel & Screen**

This is the **final, visible part**—the actual Android application you install. It brings everything together:

- It wires all the modules together using Hilt (dependency injection).
- It shows the **user interface**: buttons, screens, indicators.
  - A big “push‑to‑talk” button.
  - A squad list showing who’s nearby.
  - A panic button for emergencies.
  - A switch to arm sentry mode.
  - A screen to see the mesh network status.
- It has a **dark, high‑contrast “HUD” theme** so it’s easy to see in the field.

The `app` module is like the **dashboard of a car**—you press the pedals and turn the wheel, but the engine, transmission, and brakes (the other modules) do the heavy lifting.

---

## Summary Table

| Module | What it does (simple) |
|--------|------------------------|
| `core-domain` | Defines shared words and rules (messages, devices, languages). |
| `core-platform-api` | Lists what the phone should be able to do (record, play, send, vibrate, etc.). |
| `core-protocol` | Creates the envelope and stamp system for messages. |
| `platform-android` | Actually uses the phone’s hardware and runs the speech models. |
| `engine-discovery` | Shows who is nearby (radar). |
| `engine-mesh` | Routes messages through the squad (postal system). |
| `engine-speech` | Turns speech to text and text to speech (no translation). |
| `feature-ptt` | The walkie‑talkie button and voice‑activated mode. |
| `feature-emergency` | Panic button and emergency sirens/flashlights. |
| `feature-sentry` | Passive guard that wakes up on a distress signal. |
| `app` | The screen and wiring that makes everything work together. |

That’s the whole app in plain English!
