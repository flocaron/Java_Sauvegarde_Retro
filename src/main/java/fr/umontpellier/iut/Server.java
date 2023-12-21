package fr.umontpellier.iut;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.zip.*;

public class Server {

    private int port;

    public Server(int port) {
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Serveur en écoute sur le port " + port);

            while (true) {
                try (
                        Socket clientSocket = serverSocket.accept();
                        InputStream in = clientSocket.getInputStream();
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));
                        BufferedInputStream bufferedInputStream = new BufferedInputStream(in)
                ) {

                    switch (getCodeEnvoie(bufferedReader)) {
                        case 1 -> {
                            File receivedFile = new File("received.zip");
                            try (FileOutputStream fos = new FileOutputStream(receivedFile)) {
                                byte[] buffer = new byte[4096];
                                int count;
                                while ((count = bufferedInputStream.read(buffer)) > 0) {
                                    fos.write(buffer, 0, count);
                                }
                            }
                            // Décompression du fichier ZIP
                            unzip(receivedFile, new File("Backup"));

                            // Suppression de l'archive après décompression
                            receivedFile.delete();
                        }
                        case 2 -> {
                            String path = bufferedReader.readLine();

                            sendFolder(path, clientSocket);
                        }
                        default -> {
                            System.out.println("Wrong code");
                            return;
                        }
                    };

                    System.out.println("Dossier reçu et décompressé.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void unzip(File zipFile, File outputFolder) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                File newFile = newFile(outputFolder, zipEntry);
                if (zipEntry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    new File(newFile.getParent()).mkdirs();
                    try (FileOutputStream fileOutputStream = new FileOutputStream(newFile)) {
                        int len;
                        byte[] buffer = new byte[1024];
                        while ((len = zipInputStream.read(buffer)) > 0) {
                            fileOutputStream.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zipInputStream.getNextEntry();
            }
            zipInputStream.closeEntry();
        }
    }

    // Pour éviter la vulnérabilité Zip Slip
    private File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());
        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("L'entrée ZIP est en dehors du dossier cible");
        }
        return destFile;
    }




    // Sender
    public void sendFolder(String folderPath, Socket socket) throws IOException {
        File folder = new File("Backup\\" + folderPath);
        if (!folder.isDirectory()) {
            throw new IllegalArgumentException("Le chemin fourni n'est pas un dossier valide");
        }

        // Création d'une archive du dossier
        File zipFile = createZipFile(folder);

        // Envoi de l'archive au serveur
        sendFileToServer(zipFile, socket);

        // Suppression de l'archive temporaire après l'envoi
        zipFile.delete();
    }

    private File createZipFile(File folder) throws IOException {
        File zipFile = new File(folder.getName() + ".zip");
        try (FileOutputStream fileOutputStream = new FileOutputStream(zipFile);
             ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream)) {
            zipFolder(folder, folder.getName(), zipOutputStream);
        }
        return zipFile;
    }

    private void zipFolder(File folderToZip, String parentFolder, ZipOutputStream zos) throws IOException {
        for (File file : folderToZip.listFiles()) {
            if (file.isDirectory()) {
                zipFolder(file, parentFolder + "/" + file.getName(), zos);
                continue;
            }
            zos.putNextEntry(new ZipEntry(parentFolder + "/" + file.getName()));
            Files.copy(file.toPath(), zos);
            zos.closeEntry();
        }
    }

    private void sendFileToServer(File file, Socket socket) throws IOException {
        try (
                FileInputStream fis = new FileInputStream(file);
                BufferedInputStream bis = new BufferedInputStream(fis);
                OutputStream os = socket.getOutputStream()) {

            byte[] buffer = new byte[4096];
            int count;
            while ((count = bis.read(buffer)) > 0) {
                os.write(buffer, 0, count);
            }
            os.flush();
        }
    }



    private int getCodeEnvoie(BufferedReader in) throws IOException {
        return Integer.parseInt(in.readLine());
    }

    public static void main(String[] args) {
        Server server = new Server(12345); // Même port que le client
        server.start();
    }
}

