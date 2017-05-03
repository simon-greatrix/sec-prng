package prng.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import javax.swing.table.AbstractTableModel;

public class ConfigTable extends AbstractTableModel {
    
    static class Def {
        String property;
        String resource;
        String type;
    }

    /** Serial version UID */
    private static final long serialVersionUID = 1L;
    
    /** The Resources for the UI */
    ResourceBundle resources;
    
    /** The Resources for the UI */
    private ResourceBundle labels;
    
    /** The Resources for the UI */
    private ResourceBundle descriptions;
    
    /** The configuration property definitions */
    private Def[] defs;
    
    /** The configuration */
    private Map<String,String> config;
    
    /** The source of the configuration */
    private Map<String,URI> sources;
    
    public ConfigTable() {
        String packageName = ConfigTable.class.getPackage().getName();
        resources = ResourceBundle.getBundle(ConfigTable.class.getName());
        labels = ResourceBundle.getBundle(packageName+".labels");
        descriptions = ResourceBundle.getBundle(packageName+".descriptions");
        List<String> defTxt = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(ConfigTable.class.getResourceAsStream("definitions.txt"),StandardCharsets.UTF_8))) {
            String line;
            while ((line=br.readLine()) != null) {
                line = line.trim();
                
                if( ! (line.isEmpty() || line.charAt(0)=='#') ) {
                    defTxt.add(line);
                }
            } 
        } catch ( IOException ioe ) {
            throw new ExceptionInInitializerError(ioe);
        }
        
        defs = new Def[defTxt.size() / 3];
        for(int i=0;i<defs.length;i++) {
            Def d = new Def();
            defs[i] = d;
            d.property = defTxt.get(i*3);
            d.resource = defTxt.get(i*3+1);
            d.type = defTxt.get(i*3+2);
        }
        
        PropsList props = new PropsList();
        props.load();
        config = props.get();
    }


    @Override
    public String getColumnName(int column) {
        return resources.getString("COLUMN_HEADER_"+column);
    }


    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }


    @Override
    public int getRowCount() {
        return defs.length;
    }


    @Override
    public int getColumnCount() {
        return 2;
    }


    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if( columnIndex==0 ) {
            return labels.getString(defs[rowIndex].resource);
        }
        return config.get(defs[rowIndex].property);
    }

}
