package kr.bubbleinsurance.customer;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.xmlpull.v1.XmlPullParser;
import android.util.Xml;
/** Offline spreadsheet renderer. Never executes content or sends records to a server. */
final class RecordReader {
 static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
 static Map<String,byte[]> unzip(InputStream stream)throws Exception{
  Map<String,byte[]> result=new LinkedHashMap<>();long total=0;int count=0;
  try(ZipInputStream zip=new ZipInputStream(stream)){ZipEntry e;byte[] b=new byte[8192];while((e=zip.getNextEntry())!=null){if(e.isDirectory())continue;if(++count>2000)throw new IOException("파일 항목이 너무 많습니다.");ByteArrayOutputStream out=new ByteArrayOutputStream();int n;while((n=zip.read(b))!=-1){total+=n;if(total>64*1024*1024)throw new IOException("파일 용량이 너무 큽니다.");out.write(b,0,n);}result.put(e.getName(),out.toByteArray());}}
  return result;
 }
 static XmlPullParser xml(byte[] bytes)throws Exception{String s=new String(bytes,"UTF-8");if(s.contains("<!DOCTYPE")||s.contains("<!ENTITY"))throw new IOException("지원하지 않는 문서입니다.");XmlPullParser p=Xml.newPullParser();p.setInput(new StringReader(s));return p;}
 static String sheets(Map<String,byte[]> files)throws Exception{
  List<String> strings=new ArrayList<>();byte[] shared=files.get("xl/sharedStrings.xml");
  if(shared!=null){XmlPullParser p=xml(shared);StringBuilder value=null;for(int event=p.next();event!=XmlPullParser.END_DOCUMENT;event=p.next()){if(event==XmlPullParser.START_TAG&&p.getName().equals("si"))value=new StringBuilder();else if(event==XmlPullParser.START_TAG&&p.getName().equals("t")&&value!=null)value.append(p.nextText());else if(event==XmlPullParser.END_TAG&&p.getName().equals("si")){strings.add(value.toString());value=null;}}}
  StringBuilder html=new StringBuilder();int sheet=0,cells=0;
  for(String key:new TreeSet<>(files.keySet())){if(!key.matches("xl/worksheets/sheet[0-9]+\\.xml"))continue;sheet++;html.append("<h2>진료기록 ").append(sheet).append("</h2><div class='sheet'><table>");XmlPullParser p=xml(files.get(key));String type="";int column=0;StringBuilder value=null;
   for(int event=p.next();event!=XmlPullParser.END_DOCUMENT;event=p.next()){
    if(event==XmlPullParser.START_TAG){String tag=p.getName();if(tag.equals("row")){html.append("<tr>");column=0;}else if(tag.equals("c")){String ref=p.getAttributeValue(null,"r");int target=0;if(ref!=null)for(char ch:ref.toCharArray()){if(ch<'A'||ch>'Z')break;target=target*26+ch-'A'+1;}if(target>16384)throw new IOException("문서 열이 너무 많습니다.");while(column<target-1){html.append("<td></td>");column++;}column++;type=p.getAttributeValue(null,"t");value=new StringBuilder();if(++cells>200000)throw new IOException("기록이 너무 많아 표시할 수 없습니다.");}else if((tag.equals("v")||tag.equals("t"))&&value!=null)value.append(p.nextText());}
    else if(event==XmlPullParser.END_TAG){String tag=p.getName();if(tag.equals("c")){String v=value==null?"":value.toString();if("s".equals(type)&&!v.isEmpty()){int index=Integer.parseInt(v);if(index<0||index>=strings.size())throw new IOException("문서 값이 올바르지 않습니다.");v=strings.get(index);}html.append("<td>").append(escape(v)).append("</td>");value=null;}else if(tag.equals("row"))html.append("</tr>");}
   }html.append("</table></div>");
  }
  if(sheet==0)throw new IOException("표시할 진료기록 표를 찾지 못했습니다.");return html.toString();
 }
 static String read(File file)throws Exception{
  Map<String,byte[]> entries;try(InputStream in=new FileInputStream(file)){entries=unzip(in);}
  String content;
  if(entries.containsKey("xl/workbook.xml"))content=sheets(entries);
  else{StringBuilder all=new StringBuilder();for(Map.Entry<String,byte[]> entry:entries.entrySet()){if(entry.getKey().toLowerCase(Locale.ROOT).endsWith(".xlsx")){all.append("<h2>").append(escape(entry.getKey())).append("</h2>").append(sheets(unzip(new ByteArrayInputStream(entry.getValue()))));}else if(!entry.getKey().startsWith("__MACOSX"))throw new IOException("이 압축파일 안의 형식은 아직 지원하지 않습니다.");}if(all.length()==0)throw new IOException("이 파일 형식은 아직 지원하지 않습니다.");content=all.toString();}
  return "<!doctype html><html lang='ko'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{font:18px sans-serif;color:#0d2c4d;padding:12px;background:white}h2{font-size:20px}.sheet{overflow:auto}table{border-collapse:collapse}td{padding:10px;border:1px solid #ccd7e1;white-space:pre-wrap;min-width:80px}</style><body>"+content+"</body></html>";
 }
}
