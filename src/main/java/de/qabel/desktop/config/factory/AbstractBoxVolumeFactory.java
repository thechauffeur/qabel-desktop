package de.qabel.desktop.config.factory;

import de.qabel.core.accounting.BoxClient;
import de.qabel.core.config.Identity;
import de.qabel.core.exceptions.QblInvalidCredentials;
import de.qabel.core.repository.IdentityRepository;
import de.qabel.core.repository.exception.PersistenceException;

import java.io.IOException;
import java.util.ArrayList;

public abstract class AbstractBoxVolumeFactory implements BoxVolumeFactory {
    protected BoxClient boxClient;
    protected IdentityRepository identityRepository;

    public AbstractBoxVolumeFactory(BoxClient boxClient, IdentityRepository identityRepository) {
        this.boxClient = boxClient;
        this.identityRepository = identityRepository;
    }

    public String choosePrefix(Identity identity) {
        try {
            for (String prefix : boxClient.getPrefixes()) {
                if (identity.getPrefixes().contains(prefix)) {
                    return prefix;
                }
            }

            return createNewPrefix(identity);
        } catch (Exception e) {
            throw new IllegalStateException("failed to find valid prefix: " + e.getMessage(), e);
        }
    }

    private String createNewPrefix(Identity identity) throws IOException, QblInvalidCredentials, PersistenceException {
        boxClient.createPrefix();
        ArrayList<String> prefixes = boxClient.getPrefixes();
        String prefix = prefixes.get(prefixes.size() - 1);
        identity.getPrefixes().add(prefix);
        identityRepository.save(identity);
        return prefix;
    }
}
