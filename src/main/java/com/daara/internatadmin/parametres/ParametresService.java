package com.daara.internatadmin.parametres;

import com.daara.internatadmin.audit.Audited;
import com.daara.internatadmin.parametres.dto.ParametresResponse;
import com.daara.internatadmin.parametres.dto.UpdateParametresRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ParametresService {

    private final ParametresEtablissementRepository repository;

    @Value("${app.storage.logo-dir}")
    private String logoDir;

    private static final long MAX_LOGO_SIZE = 2 * 1024 * 1024;

    public ParametresResponse get() {
        return toResponse(getEntity());
    }

    @Audited(action = "UPDATE", entityType = "Parametres")
    @Transactional
    public ParametresResponse update(UpdateParametresRequest dto) {
        ParametresEtablissement p = getEntity();
        p.setName(dto.name());
        p.setAddress(dto.address());
        p.setPhone(dto.phone());
        p.setEmail(dto.email());
        p.setDirectorName(dto.directorName());
        p.setUpdatedAt(LocalDateTime.now());
        return toResponse(p);
    }

    @Audited(action = "UPLOAD_LOGO", entityType = "Parametres")
    @Transactional
    public ParametresResponse uploadLogo(MultipartFile file) {
        validateLogo(file);

        ParametresEtablissement p = getEntity();

        try {
            Path directory = Paths.get(logoDir);
            Files.createDirectories(directory);

            String extension = file.getContentType().equals("image/png") ? ".png" : ".jpg";
            Path destination = directory.resolve("logo" + extension);

            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            p.setLogoPath(destination.toString());
            p.setUpdatedAt(LocalDateTime.now());

        } catch (IOException e) {
            throw new IllegalArgumentException("Erreur lors de l'enregistrement du logo");
        }

        return toResponse(p);
    }

    public byte[] getLogoBytes() {
        ParametresEtablissement p = getEntity();
        if (p.getLogoPath() == null) {
            throw new IllegalArgumentException("Aucun logo n'a été configuré");
        }
        try {
            return Files.readAllBytes(Paths.get(p.getLogoPath()));
        } catch (IOException e) {
            throw new IllegalArgumentException("Impossible de lire le fichier logo");
        }
    }

    public String getLogoContentType() {
        String path = getEntity().getLogoPath();
        return (path != null && path.endsWith(".png")) ? "image/png" : "image/jpeg";
    }

    private void validateLogo(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier est vide");
        }
        if (file.getSize() > MAX_LOGO_SIZE) {
            throw new IllegalArgumentException("Le logo dépasse la taille maximale de 2 Mo");
        }
        String contentType = file.getContentType();
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
            throw new IllegalArgumentException("Seuls les formats JPEG et PNG sont acceptés");
        }
    }

    private ParametresEtablissement getEntity() {
        return repository.findById(1L)
                .orElseThrow(() -> new IllegalStateException(
                        "Paramètres établissement introuvables — la migration V14 a-t-elle été appliquée ?"
                ));
    }

    private ParametresResponse toResponse(ParametresEtablissement p) {
        return new ParametresResponse(
                p.getId(), p.getName(), p.getAddress(), p.getPhone(),
                p.getEmail(), p.getDirectorName(), p.getLogoPath() != null
        );
    }
}