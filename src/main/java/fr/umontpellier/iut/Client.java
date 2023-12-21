package fr.umontpellier.iut;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.sql.SQLOutput;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.zip.*;

public class Client {

    private Socket socket;

    private BufferedReader in;
    private BufferedInputStream bufferIn;

    private PrintWriter out;

    public Client(String serverAddress, int port) {
        try {
            socket = new Socket(serverAddress, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            InputStream inputStream = socket.getInputStream();
            in = new BufferedReader(new InputStreamReader(inputStream));
            bufferIn = new BufferedInputStream(inputStream);
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
        Set<String> allowedExtensions = loadAllowedExtensions();

        File folder = new File(folderPath);
        if (!folder.isDirectory()) {
            throw new IllegalArgumentException("Le chemin fourni n'est pas un dossier valide");
        }

        // Création d'une archive du dossier
        File zipFile = createZipFile(folder, allowedExtensions);

        // Envoi de l'archive au serveur
        sendFileToServer(zipFile, socket);

        // Suppression de l'archive temporaire après l'envoi
        zipFile.delete();
    }

    private File createZipFile(File folder, Set<String> allowedExtensions) throws IOException {
        File zipFile = new File(folder.getName() + ".zip");
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zipFolder(folder, folder.getName(), zos, allowedExtensions);
        }
        return zipFile;
    }

    private void zipFolder(File folderToZip, String parentFolder, ZipOutputStream zos, Set<String> allowedExtensions) throws IOException {
        for (File file : folderToZip.listFiles()) {
            if (file.isDirectory()) {
                zipFolder(file, parentFolder + "/" + file.getName(), zos, allowedExtensions);
                continue;
            }
            String extensions = getFileExtension(file);
            if (allowedExtensions.contains(extensions)) {
                zos.putNextEntry(new ZipEntry(parentFolder + "/" + file.getName()));
                Files.copy(file.toPath(), zos);
                zos.closeEntry();
            }
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




    private void sendCode(int envoie) {
        out.println(envoie + "");
        out.flush();
    }


    private Set<String> loadAllowedExtensions() throws IOException {
        Set<String> extensions = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader("src\\main\\resources\\extensions.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                extensions.add(line.trim().toLowerCase());
            }
        }
        return extensions;
    }

    private String getFileExtension(File file) {
        String name = file.getName();
        int lastIndexOf = name.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return ""; // Fichier sans extension
        }
        return name.substring(lastIndexOf + 1).toLowerCase();
    }



    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        System.out.print("IP: ");
        String ip = scanner.nextLine();

        System.out.print("PORT: ");
        int port = Integer.parseInt(scanner.nextLine());

        System.out.print("PATH: ");
        String path = scanner.nextLine();

        System.out.print("Envoyer (1) Recevoir (2) :");
        int envoie = Integer.parseInt(scanner.nextLine());

        // Exemple d'utilisation
        Client client = new Client(ip, port);
        System.out.println("Client connecté");

        try {

            client.sendCode(envoie);

            if (envoie == 1) {
                client.sendFolder(path, client.socket);
            } else if (envoie == 2){
                client.out.println(path);
                File receivedFile = new File("client.zip");
                try (FileOutputStream fos = new FileOutputStream(receivedFile)) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = client.bufferIn.read(buffer)) > 0) {
                        fos.write(buffer, 0, count);
                    }
                }
                // Décompression du fichier ZIP
                client.unzip(receivedFile, new File("Client_Files"));

                // Suppression de l'archive après décompression
                receivedFile.delete();
            } else {
                System.out.println("Mauvais code Envoyer / Recevoir\nQuiting ....");
                System.exit(1);
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}

