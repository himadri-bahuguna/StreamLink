**StreamLink**

_**Overview**_

Sharing and managing video content across multiple computers can become inefficient when files need to be transferred manually. This project aims to develop a network-based application in which one computer acts as a central video server and shares a folder containing video files.
Other computers connected to the same local network can connect to the server, browse and search for available videos, and stream them directly through the application.

**Technology Stack**

Programming Language: Java 

Database: MySQL 

Networking: TCP/IP

Architecture: Client-Server Architecture

Video Storage: Local File System

## How to Run the Project

### Prerequisites

- Java JDK installed and configured.
- Maven or Maven Daemon (mvnd) installed.
- JavaFX dependencies configured in `pom.xml`.
- All required project dependencies available.

### Setup Instructions

1. Clone the repository:

   ```bash
   git clone <repository-url>
   ```

2. Navigate to the project directory:

   ```bash
   cd StreamLink
   ```

3. Ensure that the required dependencies are configured in `pom.xml`.

### Running the Application

The application follows a Client-Server architecture. Start the server before launching the client application.

**Step 1: Start the Video Server**

Open a terminal in the project directory and execute:

```powershell
mvnd exec:java "-Dexec.mainClass=com.streamlink.client.StreamServer" 
```

**Step 2: Launch the Client Application**

Open another terminal and execute:

```powershell
mvnd javafx:run "-Djavafx.mainClass=com.streamlink.client.StreamLinkGUI"
```

**Step 3: Connect to the Server**

- Ensure both computers are connected to the same local network.
- Enter the server's IP address and port number in the client application, if required.
- Browse the available videos.
- Select and play the desired video.

### Notes

- The server computer must remain active while clients are accessing videos.
- Ensure that the required network ports are accessible through the firewall.
- Configure the shared video directory before starting the server.
- Use the server's local IP address when connecting from another computer.
