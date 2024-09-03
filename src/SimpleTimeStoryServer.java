import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class SimpleTimeStoryServer {

    public static void main(String[] args) {
        try {
            // Start the server on port 8080
            ServerSocket serverSocket = new ServerSocket(8080);
            System.out.println("Server started on port 8080");

            while (true) {
                // Accept incoming connections
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from " + clientSocket.getInetAddress());

                // Handle the request
                handleClientRequest(clientSocket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClientRequest(Socket clientSocket) throws IOException {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        ) {
            // Read the HTTP request (this simple server ignores most of it)
            String line;
            while (!(line = in.readLine()).isEmpty()) {
                System.out.println(line);
            }

            // Fetch and parse the latest stories
            List<Story> stories = fetchLatestStories();

            // Send HTTP response headers
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: application/json");
            out.println("Connection: close");
            out.println();

            // Send the JSON response
            out.println(serializeStoriesToJson(stories));

        } finally {
            clientSocket.close();
        }
    }

    private static List<Story> fetchLatestStories() {
        List<Story> stories = new ArrayList<>();
        try {
            // Connect to time.com on port 80
            Socket socket = new Socket("time.com", 80);
            OutputStream outputStream = socket.getOutputStream();
            InputStream inputStream = socket.getInputStream();

            // Send HTTP GET request with Accept-Encoding header
            String request = "GET / HTTP/1.1\r\n" +
                    "Host: time.com\r\n" +
                    "Accept-Encoding: gzip, deflate\r\n" +
                    "Connection: close\r\n\r\n";
            outputStream.write(request.getBytes());
            outputStream.flush();

            // Read the response headers
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            boolean isGzip = false;
            boolean isHtml = false;

            // Read headers to determine content type and encoding
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                System.out.println(line);  // Debug output to see headers
                if (line.toLowerCase().contains("content-encoding: gzip")) {
                    isGzip = true;
                }
                if (line.toLowerCase().contains("content-type: text/html")) {
                    isHtml = true;
                }
            }

            // Check if response is HTML
            if (!isHtml) {
                System.out.println("Failed to fetch valid HTML content.");
                socket.close();
                return stories;  // Early exit if the content is not HTML
            }

            // Read the response body
            InputStream responseBodyStream = isGzip ? new GZIPInputStream(inputStream) : inputStream;
            StringBuilder responseBuilder = new StringBuilder();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = responseBodyStream.read(buffer)) != -1) {
                responseBuilder.append(new String(buffer, 0, bytesRead));
            }

            socket.close();
            String htmlContent = responseBuilder.toString();
            System.out.println("HTML Content Received:\n" + htmlContent);  // Debug output to see HTML content

            // Extract stories from the HTML content
            extractStories(htmlContent, stories);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return stories;
    }

    private static void extractStories(String html, List<Story> stories) {
        int index = 0;
        while (stories.size() < 6 && index != -1) {
            // Locate the <a> tag that has the stories (use a more precise pattern based on actual HTML structure)
            index = html.indexOf("<a href=\"https://time.com/", index);
            if (index != -1) {
                int startLink = html.indexOf("href=\"", index) + 6;  // Skip 'href="'
                int endLink = html.indexOf("\"", startLink);
                String link = html.substring(startLink, endLink);

                int startTitle = html.indexOf(">", endLink) + 1;
                int endTitle = html.indexOf("<", startTitle);
                String title = html.substring(startTitle, endTitle).trim();

                if (!title.isEmpty() && title.length() > 1) { // Ensure title is not just whitespace
                    stories.add(new Story(title, link));
                }
                index = endLink;
            }
        }
    }

    private static String serializeStoriesToJson(List<Story> stories) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        for (int i = 0; i < stories.size(); i++) {
            Story story = stories.get(i);
            json.append("{\"title\": \"").append(story.getTitle()).append("\", ");
            json.append("\"link\": \"").append(story.getLink()).append("\"}");
            if (i < stories.size() - 1) {
                json.append(", ");
            }
        }
        json.append("]");
        return json.toString();
    }

    static class Story {
        private String title;
        private String link;

        public Story(String title, String link) {
            this.title = title;
            this.link = link;
        }

        public String getTitle() {
            return title;
        }

        public String getLink() {
            return link;
        }
    }
}
