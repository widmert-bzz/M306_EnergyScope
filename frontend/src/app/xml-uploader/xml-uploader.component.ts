import { Component } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-xml-uploader',
  templateUrl: './xml-uploader.component.html',
  styleUrls: ['./xml-uploader.component.css']
})
export class XmlUploaderComponent {
  parsedFiles: { name: string; content: any }[] = []; // Declare the property
  uploadStatus: { [filename: string]: 'pending' | 'success' | 'error' } = {};
  uploadMessages: string[] = [];

  constructor(private http: HttpClient) {}

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.parsedFiles = []; // Clear previous files
      this.uploadMessages = []; // Clear previous messages
      this.uploadStatus = {}; // Clear previous status

      const files = Array.from(input.files);

      files.forEach(file => {
        const reader = new FileReader();
        reader.onload = () => {
          const xmlString = reader.result as string;
          const parsedContent = this.parseXml(xmlString);
          this.parsedFiles.push({ name: file.name, content: parsedContent });
          this.uploadFile(file);
        };
        reader.readAsText(file);
      });
    }
  }

  uploadFile(file: File): void {
    const formData = new FormData();
    formData.append('file', file);
    this.uploadStatus[file.name] = 'pending';

    this.http.post('http://localhost:8080/upload', formData)
      .subscribe({
        next: (response) => {
          this.uploadStatus[file.name] = 'success';
          this.uploadMessages.push(`${file.name} wurde erfolgreich hochgeladen.`);
        },
        error: (error) => {
          this.uploadStatus[file.name] = 'error';
          this.uploadMessages.push(`Fehler beim Hochladen von ${file.name}: ${error.message}`);
          console.error('Upload error:', error);
        }
      });
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
