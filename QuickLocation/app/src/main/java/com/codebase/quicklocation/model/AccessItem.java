package com.codebase.quicklocation.model;

/**
 * Created by Spanky on 10/01/2017.
 * Representa un item del menu principal (pantalla)
 * Designa el logo y el titulo del boton mostrado
 */

public class AccessItem {
    public String itemName;
    public int itemLogo;

    public AccessItem(String itemName, int itemLogo) {
        this.itemName = itemName;
        this.itemLogo = itemLogo;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public int getItemLogo() {
        return itemLogo;
    }

    public void setItemLogo(int itemLogo) {
        this.itemLogo = itemLogo;
    }
}