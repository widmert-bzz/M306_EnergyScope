import { Component } from '@angular/core';

@Component({
  selector: 'app-xml-uploader',
  templateUrl: './xml-uploader.component.html',
  styleUrls: ['./xml-uploader.component.css']
})
export class XmlUploaderComponent {
  parsedFiles: { name: string; content: any }[] = []; // Declare the property

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.parsedFiles = []; // Clear previous files
      Array.from(input.files).forEach(file => {
        const reader = new FileReader();
        reader.onload = () => {
          const xmlString = reader.result as string;
          const parsedContent = this.parseXml(xmlString); // Define parsedContent here
          this.parsedFiles.push({ name: file.name, content: parsedContent });
        };
        reader.readAsText(file);
      });
    }
  }

  parseXml(xmlString: string): any {
    const parser = new DOMParser();
    const xmlDoc = parser.parseFromString(xmlString, 'application/xml');
    return this.xmlToJson(xmlDoc);
  }

  xmlToJson(xml: Node): any {
    const obj: any = {};
    if (xml.nodeType === 1) { // Element
      const element = xml as Element;
      if (element.attributes.length > 0) {
        obj['@attributes'] = {};
        for (let j = 0; j < element.attributes.length; j++) {
          const attribute = element.attributes.item(j);
          if (attribute) {
            obj['@attributes'][attribute.nodeName] = attribute.nodeValue;
          }
        }
      }
    } else if (xml.nodeType === 3) { // Text
      const textContent = xml.nodeValue?.trim();
      if (textContent) {
        return textContent;
      }
      return null;
    }

    if (xml.hasChildNodes()) {
      for (let i = 0; i < xml.childNodes.length; i++) {
        const item = xml.childNodes.item(i);
        const nodeName = item.nodeName;
        const childObject = this.xmlToJson(item);
        if (childObject !== null) {
          if (typeof obj[nodeName] === 'undefined') {
            obj[nodeName] = childObject;
          } else {
            if (!Array.isArray(obj[nodeName])) {
              obj[nodeName] = [obj[nodeName]];
            }
            obj[nodeName].push(childObject);
          }
        }
      }
    }
    return obj;
  }
}
