package com.browserup.bup.assertion.supplier;

import com.browserup.harreader.filter.HarLogFilter;
import com.browserup.harreader.model.Har;
import com.browserup.harreader.model.HarEntry;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class MostRecentUrlFilteredHarEntrySupplier extends UrlFilteredHarEntriesSupplier {

    public MostRecentUrlFilteredHarEntrySupplier(Har har, Pattern pattern) {
        super(har, pattern);
    }

    @Override
    public List<HarEntry> get() {
        return HarLogFilter.findMostRecentEntry(getHar().getLog(), getPattern()).
                map(Collections::singletonList).
                orElse(Collections.emptyList());
    }
}
