package com.codegym.jira.bugtracking.attachment;

import com.codegym.jira.common.error.IllegalRequestDataException;
import com.codegym.jira.common.error.NotFoundException;
import lombok.experimental.UtilityClass;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

@UtilityClass
public class FileUtil {
    private static final String ATTACHMENT_PATH = "./attachments/%s/";

    /*
    Funcion original
    public static void upload(MultipartFile multipartFile, String directoryPath, String fileName) {
        if (multipartFile.isEmpty()) {
            throw new IllegalRequestDataException("Seleccione un archivo a cargar");
        }

        File dir = new File(directoryPath);
        if (dir.exists() || dir.mkdirs()) {
            File file = new File(directoryPath + fileName);
            try (OutputStream outStream = new FileOutputStream(file)) {
                outStream.write(multipartFile.getBytes());
            } catch (IOException ex) {
                throw new IllegalRequestDataException("Error en carga de archivo: " + multipartFile.getOriginalFilename());
            }
        }
    }
     */

    public static void upload(MultipartFile multipartFile, Path directoryPath, String fileName) {
        // Validaciones más exhaustivas
        if (multipartFile == null) {
            throw new IllegalArgumentException("El archivo no puede estar vacio");
        }

        if (multipartFile.isEmpty()) {
            throw new IllegalRequestDataException("Seleccione un archivo a cargar");
        }

        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de archivo no puede estar vacio o ser nulo");
        }

        if (directoryPath == null) {
            throw new IllegalArgumentException("El directorio no puede estar vacio");
        }

        // Sanitizar el nombre del archivo
        String sanitizedFileName = sanitizeFileName(fileName);

        try {
            // Crear directorios si no existen (forma más segura)
            Files.createDirectories(directoryPath);

            // Validar que el directorio es seguro y tiene permisos adecuados
            validateDirectorySecurity(directoryPath);

            // Construir la ruta completa del archivo
            Path filePath = directoryPath.resolve(sanitizedFileName);

            // Validar que no estamos escribiendo fuera del directorio destino
            validatePathWithinDirectory(filePath, directoryPath);

            // Copiar el archivo de forma segura usando NIO
            Files.copy(
                    multipartFile.getInputStream(),
                    filePath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            // Establecer permisos seguros (opcional, dependiendo del sistema operativo)
            setSecureFilePermissions(filePath);

        } catch (IOException ex) {
            String errorMessage = String.format("Error en la carga de archivo: %s. Error: %s",
                    multipartFile.getOriginalFilename(), ex.getMessage());

            // Buscar el constructor apropiado
            try {
                // Intentar constructor con mensaje y causa
                throw new IllegalRequestDataException(errorMessage);
            } catch (IllegalArgumentException e) {
                // Si no existe, usar constructor solo con mensaje
                throw new IllegalRequestDataException(errorMessage);
            }
        }
    }

// Métodos auxiliares para mejorar la seguridad

    private static String sanitizeFileName(String fileName) {
        // Remover caracteres potencialmente peligrosos
        String sanitized = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");

        // Prevenir path traversal attacks
        sanitized = sanitized.replace("..", "_");

        // Limitar la longitud del nombre
        return sanitized.length() > 255 ? sanitized.substring(0, 255) : sanitized;
    }

    private static void validateDirectorySecurity(Path directoryPath) throws IOException {
        // Verificar que el directorio no es un enlace simbólico (a menos que sea intencional)
        if (Files.isSymbolicLink(directoryPath)) {
            throw new SecurityException("La ruta de directorio no puede ser un link simbolico");
        }

        // Verificar permisos de escritura
        if (!Files.isWritable(directoryPath)) {
            throw new SecurityException("No tiene permisos en el directorio: " + directoryPath);
        }
    }

    private static void validatePathWithinDirectory(Path filePath, Path baseDirectory) throws IOException {
        // Prevenir path traversal attacks
        if (!filePath.normalize().startsWith(baseDirectory.normalize())) {
            throw new SecurityException("Ruta de archivo válida: Se intento ataque de tipo path traversal");
        }
    }

    private static void setSecureFilePermissions(Path filePath) throws IOException {
        // Establecer permisos seguros (ejemplo para sistemas Unix-like)
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(filePath, permissions);
        } catch (UnsupportedOperationException e) {
            // No soportado en este sistema de archivos, ignorar silenciosamente
        }
    }

    public static Resource download(String fileLink) {
        Path path = Paths.get(fileLink);
        try {
            Resource resource = new UrlResource(path.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new IllegalRequestDataException("Failed to download file " + resource.getFilename());
            }
        } catch (MalformedURLException ex) {
            throw new NotFoundException("File" + fileLink + " not found");
        }
    }

    public static void delete(String fileLink) {
        Path path = Paths.get(fileLink);
        try {
            Files.delete(path);
        } catch (IOException ex) {
            throw new IllegalRequestDataException("File" + fileLink + " deletion failed.");
        }
    }

    public static String getPath(String titleType) {
        return String.format(ATTACHMENT_PATH, titleType.toLowerCase());
    }
}
