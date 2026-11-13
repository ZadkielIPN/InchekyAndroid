package com.example.inchekyandroid.ui.home;

import com.google.gson.JsonObject;

public final class PageInfo {
    public final int number;
    public final int totalPages;
    public final boolean requiresReload;

    private PageInfo(int number, int totalPages, boolean requiresReload) {
        this.number = number;
        this.totalPages = totalPages;
        this.requiresReload = requiresReload;
    }

    public static PageInfo from(JsonObject response, int requestedPage) {
        int total = response.has("totalPages")
                ? response.get("totalPages").getAsInt()
                : 1;
        int responsePage = response.has("number")
                ? response.get("number").getAsInt()
                : requestedPage;

        if (total <= 0) {
            return new PageInfo(0, 1, requestedPage > 0);
        }

        int lastPage = total - 1;
        if (requestedPage > lastPage || responsePage > lastPage) {
            return new PageInfo(lastPage, total, true);
        }
        if (responsePage < 0) {
            return new PageInfo(0, total, true);
        }
        return new PageInfo(responsePage, total, false);
    }

    public boolean hasPrevious() {
        return number > 0;
    }

    public boolean hasNext() {
        return number + 1 < totalPages;
    }
}
