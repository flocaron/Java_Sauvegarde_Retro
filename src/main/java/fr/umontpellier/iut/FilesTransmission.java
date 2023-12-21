package fr.umontpellier.iut;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class FilesTransmission {


    // Recieve
    private void getFile(BufferedInputStream buffierIn) throws IOException {
        File receivedFile = new File("received.zip");
        FileOutputStream fileOutputStream = new FileOutputStream(receivedFile);
        byte[] buffer = new byte[4096];
        int count;
        while ((count = buffierIn.read(buffer)) > 0) {
            fileOutputStream.write(buffer, 0, count);
        }

        // Décompression du fichier ZIP
        unzip(receivedFile, new File("unzipped_folder"));

        // Suppression de l'archive après décompression
        System.out.println("Zip suppress " + receivedFile.delete());
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
        File folder = new File(folderPath);
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
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zipFolder(folder, folder.getName(), zos);
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


}