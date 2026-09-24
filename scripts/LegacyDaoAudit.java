import it.polimi.tiw.dao.*;
import java.sql.*;
import java.nio.file.*;
import javax.xml.parsers.*;
import java.util.*;
/** Audit legacy DAO methods against a DISPOSABLE database; config is external. */
public class LegacyDaoAudit {
  static int passed=0,failed=0;
  interface Test {void run() throws Exception;}
  static void check(String name,Test test){try{test.run();passed++;System.out.println("PASS "+name);}catch(Exception|AssertionError e){failed++;System.out.println("FAIL "+name+": "+e.getClass().getSimpleName()+" "+(e instanceof SQLException ? ((SQLException)e).getSQLState():e.getMessage()));}}
  static void expect(boolean result,String detail){if(!result)throw new AssertionError(detail);}
  public static void main(String[] args)throws Exception{
    if(args.length!=2 || !args[1].equals("--allow-temporary-data"))throw new IllegalArgumentException("Pass disposable Tomcat context XML and --allow-temporary-data");
    var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
    var doc=factory.newDocumentBuilder().parse(Path.of(args[0]).toFile());var params=doc.getElementsByTagName("Parameter");Map<String,String> conf=new HashMap<>();
    for(int i=0;i<params.getLength();i++){var el=(org.w3c.dom.Element)params.item(i);conf.put(el.getAttribute("name"),el.getAttribute("value"));}
    if(!conf.get("dbUrl").startsWith("jdbc:mysql://127.0.0.1:"))throw new IllegalArgumentException("Loopback only");
    try(Connection c=DriverManager.getConnection(conf.get("dbUrl"),conf.get("dbUser"),conf.get("dbPassword"))){
      var users=new UserDAO(c);var folders=new FolderDAO(c);var docs=new DocumentDAO(c);String user="legacy"+Long.toString(System.nanoTime(),36);int id=users.registerUser(user+"@test.invalid","Synthetic_7",user);
      check("empty getLastDocumentID returns documented -1",()->expect(docs.getLastDocumentID(id)==-1,"returned "+docs.getLastDocumentID(id)));
      check("empty getLastFolderID returns documented -1",()->expect(folders.getLastFolderID(id)==-1,"returned "+folders.getLastFolderID(id)));
      folders.createHomePageFolder(id);int root=folders.getLastFolderID(id);
      check("legacy tree creation for registered account",()->expect(folders.getFolderTree(id).getFolder().getFolderID()==root,"wrong root"));
      folders.createFolder(id,"Older child",root,1);int child=folders.getLastFolderID(id);folders.createFolder(id,"Newer parent",root,1);int parent=folders.getLastFolderID(id);
      try(var s=c.prepareStatement("UPDATE Folder SET parent_folder_id=?,depth=2 WHERE folder_id=?")){s.setInt(1,parent);s.setInt(2,child);s.executeUpdate();}
      check("legacy deleteFolder handles a moved older child",()->{folders.deleteFolder(id,parent);expect(folders.findFolderByID(id,parent)==null,"not deleted");});
      check("legacy null document list composes with tree builder",()->folders.getFolderTree(id).setDocumentList(docs.getAllDocuments(id,root)));
      check("legacy document CRUD owner isolation",()->{docs.createDocument(id,"audit","summary","txt",root);int document=docs.getLastDocumentID(id);expect(docs.findDocumentByID(id+1000000,document)==null,"ownership failure");expect(docs.findDocumentByID(id,document)!=null,"missing document");docs.deleteDocument(id,document);expect(docs.findDocumentByID(id,document)==null,"not deleted");});
      check("legacy passwords upgrade on exact successful login",()->{
        String name="pw"+Long.toString(System.nanoTime(),36);
        try(var insert=c.prepareStatement("INSERT INTO User(username,email,password) VALUES(?,?,?)")) {
          insert.setString(1,name);insert.setString(2,name+"@test.invalid");insert.setString(3,"Legacy é ");insert.executeUpdate();
        }
        expect(users.checkLogin(name,"Legacy é")==null,"trailing-space mismatch authenticated");
        expect(users.checkLogin(name,"Legacy é ")!=null,"legacy login failed");
        try(var query=c.prepareStatement("SELECT password FROM User WHERE username=?")) {
          query.setString(1,name);
          try(var rows=query.executeQuery()) {
            rows.next();expect(rows.getString(1).startsWith("pbkdf2-sha256$"),"password was not upgraded");
          }
        }
        expect(users.checkLogin(name,"Legacy é ")!=null,"upgraded login failed");
        expect(users.checkLogin(name,"Legacy é")==null,"hash ignored trailing space");
      });
      check("legacy folder delete preserves its caller transaction",()->{
        folders.createFolder(id,"Caller transaction",root,1);int f=folders.getLastFolderID(id);
        c.setAutoCommit(false);
        try {folders.deleteFolder(id,f);expect(!c.getAutoCommit(),"changed caller auto-commit");c.rollback();}
        finally {c.setAutoCommit(true);}
        expect(folders.findFolderByID(id,f)!=null,"committed caller transaction unexpectedly");
      });

    }
    System.out.println("passed="+passed+" failed="+failed);System.exit(failed==0?0:1);
  }
}
