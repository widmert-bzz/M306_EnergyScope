import { Injectable } from '@angular/core';
import { HttpClient, HttpEventType, HttpRequest } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { DataRefreshService } from '../shared/data-refresh.service';

@Injectable({
  providedIn: 'root'
})
export class XmlUploaderService {
  private parsedFilesSubject = new BehaviorSubject<{ name: string; content: any }[]>([]);
  private uploadStatusSubject = new BehaviorSubject<{ [filename: string]: 'pending' | 'success' | 'error' }>({});
  private uploadProgressSubject = new BehaviorSubject<{ [filename: string]: number }>({});
  private errorMessagesSubject = new BehaviorSubject<{ [filename: string]: string }>({});
  private overallProgressSubject = new BehaviorSubject<number>(0);

  // Observable streams
  parsedFiles$ = this.parsedFilesSubject.asObservable();
  uploadStatus$ = this.uploadStatusSubject.asObservable();
  uploadProgress$ = this.uploadProgressSubject.asObservable();
  errorMessages$ = this.errorMessagesSubject.asObservable();
  overallProgress$ = this.overallProgressSubject.asObservable();

  constructor(
    private http: HttpClient,
    private dataRefreshService: DataRefreshService
  ) {}

  // Getters for current values
  get parsedFiles(): { name: string; content: any }[] {
    return this.parsedFilesSubject.value;
  }

  get uploadStatus(): { [filename: string]: 'pending' | 'success' | 'error' } {
    return this.uploadStatusSubject.value;
  }

  get uploadProgress(): { [filename: string]: number } {
    return this.uploadProgressSubject.value;
  }

  get errorMessages(): { [filename: string]: string } {
    return this.errorMessagesSubject.value;
  }

  get overallProgress(): number {
    return this.overallProgressSubject.value;
  }

  // Method to get error message for a specific file
  getErrorMessage(filename: string): string {
    return this.errorMessages[filename] || '';
  }

  // Method to get upload progress for a specific file
  getUploadProgress(filename: string): number {
    return this.uploadProgress[filename] || 0;
  }

  // Clear all data
  clearData(): void {
    this.parsedFilesSubject.next([]);
    this.uploadStatusSubject.next({});
    this.uploadProgressSubject.next({});
    this.errorMessagesSubject.next({});
    this.overallProgressSubject.next(0);
  }

  // Calculate and update overall progress
  private updateOverallProgress(): void {
    const progress = this.uploadProgress;
    const files = this.parsedFiles;

    if (files.length === 0) {
      this.overallProgressSubject.next(0);
      return;
    }

    let totalProgress = 0;
    for (const file of files) {
      totalProgress += progress[file.name] || 0;
    }

    const overallProgress = Math.round(totalProgress / files.length);
    this.overallProgressSubject.next(overallProgress);
  }

  // Process selected files
  processFiles(files: File[]): void {
    this.clearData();

    let filesProcessed = 0;
    const totalFiles = files.length;
    const parsedFilesList: { name: string; content: any }[] = [];

    files.forEach(file => {
      const reader = new FileReader();
      reader.onload = () => {
        const xmlString = reader.result as string;
        const parsedContent = this.parseXml(xmlString);

        // Add to our local list
        parsedFilesList.push({ name: file.name, content: parsedContent });

        // Check if all files have been processed
        filesProcessed++;

        // Update parsed files immediately to display them
        this.parsedFilesSubject.next([...parsedFilesList]);

        // Only start uploading files after all files have been processed and displayed
        if (filesProcessed === totalFiles) {
          // Now start uploading all files
          setTimeout(() => {
            parsedFilesList.forEach(parsedFile => {
              const fileToUpload = files.find(f => f.name === parsedFile.name);
              if (fileToUpload) {
                this.uploadFile(fileToUpload);
              }
            });
          }, 0);
        }
      };
      reader.readAsText(file);
    });
  }

  // Upload a file
  private uploadFile(file: File): void {
    const formData = new FormData();
    formData.append('file', file);

    // Update upload status
    const currentStatus = { ...this.uploadStatus };
    currentStatus[file.name] = 'pending';
    this.uploadStatusSubject.next(currentStatus);

    // Update upload progress
    const currentProgress = { ...this.uploadProgress };
    currentProgress[file.name] = 0;
    this.uploadProgressSubject.next(currentProgress);

    // Create a request with reportProgress option
    const req = new HttpRequest('POST', 'http://localhost:8080/upload', formData, {
      reportProgress: true
    });

    this.http.request(req)
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress) {
            // Calculate and update progress percentage
            if (event.total) {
              const currentProgress = { ...this.uploadProgress };
              currentProgress[file.name] = Math.round(100 * event.loaded / event.total);
              this.uploadProgressSubject.next(currentProgress);
              this.updateOverallProgress();
            }
          } else if (event.type === HttpEventType.Response) {
            // Upload complete
            const currentProgress = { ...this.uploadProgress };
            currentProgress[file.name] = 100;
            this.uploadProgressSubject.next(currentProgress);
            this.updateOverallProgress();

            const currentStatus = { ...this.uploadStatus };
            currentStatus[file.name] = 'success';
            this.uploadStatusSubject.next(currentStatus);

            // Notify other components that data has been refreshed
            this.dataRefreshService.refreshData();
          }
        },
        error: (error) => {
          const currentStatus = { ...this.uploadStatus };
          currentStatus[file.name] = 'error';
          this.uploadStatusSubject.next(currentStatus);

          const currentErrorMessages = { ...this.errorMessages };
          currentErrorMessages[file.name] = error.message || 'Unbekannter Fehler';
          this.errorMessagesSubject.next(currentErrorMessages);
        }
      });
  }

  // Parse XML
  private parseXml(xmlString: string): any {
    const parser = new DOMParser();
    const xmlDoc = parser.parseFromString(xmlString, 'application/xml');
    return this.xmlToJson(xmlDoc);
  }

  // Convert XML to JSON
  private xmlToJson(xml: Node): any {
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
