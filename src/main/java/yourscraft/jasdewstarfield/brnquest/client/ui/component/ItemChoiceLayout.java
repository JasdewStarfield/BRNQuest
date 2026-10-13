package yourscraft.jasdewstarfield.brnquest.client.ui.component;

/** Fixed editor chrome is budgeted before the candidate viewport, including all four inventory rows. */
public record ItemChoiceLayout(UiRect panel,boolean editing) {
    public static final int SLOT_SIZE=18,COLUMNS=9;
    public UiRect candidates() {
        int top=panel.top()+(editing?89:27),left=panel.centerX()-COLUMNS*SLOT_SIZE/2;
        // Header 89 + hint gap 26 + inventory 72 + footer 22 + separation 5 = 214 pixels.
        int available=panel.height()-(editing?214:61);
        int height=Math.max(0,Math.min(editing?54:81,available));
        return new UiRect(left,top,left+COLUMNS*SLOT_SIZE,top+height);
    }
    public UiRect inventorySlot(int index) {
        if(index<0 || index>=36) throw new IllegalArgumentException("Inventory index");
        int left=candidates().left(),top=candidates().bottom()+26;
        int column=index<9?index:(index-9)%9,row=index<9?3:(index-9)/9;
        return new UiRect(left+column*SLOT_SIZE,top+row*SLOT_SIZE,left+(column+1)*SLOT_SIZE,top+(row+1)*SLOT_SIZE);
    }
    /** The inventory never renders or accepts pointer input through the fixed footer in an unusually short window. */
    public UiRect inventoryClip() {
        int top=candidates().bottom()+26;
        return new UiRect(candidates().left(),top,candidates().right(),Math.max(top,panel.bottom()-27));
    }
}
