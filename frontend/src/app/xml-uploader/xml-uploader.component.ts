import { Component, AfterViewInit, Input, OnInit, OnDestroy } from '@angular/core';
import { XmlUploaderService } from './xml-uploader.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-xml-uploader',
  templateUrl: './xml-uploader.component.html',
  styleUrls: ['./xml-uploader.component.css']
})
export class XmlUploaderComponent implements AfterViewInit, OnInit, OnDestroy {
  @Input() displayMode: 'button' | 'files' | 'both' = 'both'; // Control which part to display

  parsedFiles: { name: string; content: any }[] = [];
  uploadStatus: { [filename: string]: 'pending' | 'success' | 'error' } = {};
  uploadProgress: { [filename: string]: number } = {};
  uploadMessages: string[] = [];
  errorMessages: { [filename: string]: string } = {};

  private subscriptions: Subscription[] = [];

  constructor(private uploaderService: XmlUploaderService) {}

  ngOnInit() {
    // Subscribe to service observables
    this.subscriptions.push(
      this.uploaderService.parsedFiles$.subscribe(files => {
        this.parsedFiles = files;
        // Set up tooltip listeners after a short delay to ensure DOM is updated
        setTimeout(() => {
          this.setupTooltipListeners();
        }, 300);
      })
    );

    this.subscriptions.push(
      this.uploaderService.uploadStatus$.subscribe(status => {
        this.uploadStatus = status;
      })
    );

    this.subscriptions.push(
      this.uploaderService.uploadProgress$.subscribe(progress => {
        this.uploadProgress = progress;
      })
    );

    this.subscriptions.push(
      this.uploaderService.errorMessages$.subscribe(messages => {
        this.errorMessages = messages;
      })
    );
  }

  ngAfterViewInit() {
    this.setupTooltipListeners();
  }

  ngOnDestroy() {
    // Unsubscribe from all subscriptions
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }

  // Set up event listeners for tooltips
  setupTooltipListeners() {
    setTimeout(() => {
      const tooltips = document.querySelectorAll('.tooltip');
      tooltips.forEach(tooltip => {
        tooltip.addEventListener('mouseenter', (event) => {
          const tooltipElement = tooltip as HTMLElement;
          const tooltipText = tooltipElement.querySelector('.tooltiptext') as HTMLElement;
          if (tooltipText) {
            this.positionTooltip(event as MouseEvent, tooltipText, tooltipElement);
          }
        });
      });
    }, 500); // Small delay to ensure DOM is ready
  }

  // Position tooltip based on mouse position and viewport
  positionTooltip(event: MouseEvent, tooltipText: HTMLElement, tooltipElement: HTMLElement) {
    const rect = tooltipElement.getBoundingClientRect();
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;

    // Calculate position
    let left = event.clientX;
    let top = event.clientY - 10; // 10px above cursor

    // Adjust if tooltip would go off screen
    const tooltipWidth = tooltipText.offsetWidth;
    const tooltipHeight = tooltipText.offsetHeight;

    // Ensure tooltip doesn't go off right edge
    if (left + tooltipWidth > viewportWidth - 20) {
      left = viewportWidth - tooltipWidth - 20;
    }

    // Ensure tooltip doesn't go off top edge
    if (top - tooltipHeight < 20) {
      top = 20 + tooltipHeight;
    }

    // Set position
    tooltipText.style.left = `${left}px`;
    tooltipText.style.top = `${top - tooltipHeight}px`;
  }

  // Method to get error message for a specific file
  getErrorMessage(filename: string): string {
    return this.uploaderService.getErrorMessage(filename);
  }

  // Method to get upload progress for a specific file
  getUploadProgress(filename: string): number {
    return this.uploaderService.getUploadProgress(filename);
  }

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const files = Array.from(input.files);
      this.uploaderService.processFiles(files);
    }
  }
}
