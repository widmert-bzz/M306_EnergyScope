import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { XmlUploaderService } from './xml-uploader/xml-uploader.service';
import { Subscription } from 'rxjs';
import { XmlUploaderComponent } from './xml-uploader/xml-uploader.component';
import { SidebarService } from './shared/sidebar.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'frontend';
  sidebarOpen = false;

  @ViewChild('xmlUploader') xmlUploader!: XmlUploaderComponent;

  uploadStatus: { [filename: string]: 'pending' | 'success' | 'error' } = {};
  uploadProgress: { [filename: string]: number } = {};
  startTimes: { [filename: string]: number } = {};
  uploadStartTime: number = 0; // Time when the first file started uploading

  // Properties for smoothing time estimates
  private lastEstimatedTime: number = 0; // Last calculated time in milliseconds
  private estimateHistory: number[] = []; // History of recent estimates for averaging
  private readonly MAX_HISTORY_LENGTH = 5; // Maximum number of estimates to keep for averaging

  private subscriptions: Subscription[] = [];

  constructor(
    private uploaderService: XmlUploaderService,
    private sidebarService: SidebarService
  ) {}

  ngOnInit() {
    // Subscribe to upload status and progress
    this.subscriptions.push(
      this.uploaderService.uploadStatus$.subscribe(status => {
        this.uploadStatus = status;

        // Set upload start time when first file starts uploading
        if (this.uploadStartTime === 0 && Object.values(status).some(s => s === 'pending')) {
          this.uploadStartTime = Date.now();
        }
      })
    );

    // Subscribe to sidebar toggle events
    this.subscriptions.push(
      this.sidebarService.toggleSidebar$.subscribe(open => {
        if (open !== undefined) {
          this.sidebarOpen = open;
        } else {
          this.toggleSidebar();
        }
      })
    );

    this.subscriptions.push(
      this.uploaderService.uploadProgress$.subscribe(progress => {
        // Record start time for new uploads
        Object.keys(progress).forEach(filename => {
          if (progress[filename] > 0 && progress[filename] < 100 && !this.startTimes[filename]) {
            this.startTimes[filename] = Date.now();
          }
        });

        this.uploadProgress = progress;
      })
    );
  }

  ngOnDestroy() {
    // Unsubscribe from all subscriptions
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }

  toggleSidebar() {
    this.sidebarOpen = !this.sidebarOpen;
  }

  // Close sidebar when clicking outside
  closeOnOutsideClick(event: MouseEvent) {
    // Only close if the sidebar is open and the click is on the overlay background
    if (this.sidebarOpen && event.target === event.currentTarget) {
      this.sidebarOpen = false;
      // Also close the XML uploader overlay
      this.sidebarService.toggleSidebar(false);
    }
  }

  // Check if there are any files currently being uploaded
  hasUploadingFiles(): boolean {
    return Object.values(this.uploadStatus).some(status => status === 'pending');
  }

  // Calculate the overall progress percentage
  getOverallProgress(): number {
    const files = Object.keys(this.uploadProgress);
    if (files.length === 0) return 0;

    const totalProgress = files.reduce((sum, filename) => sum + (this.uploadProgress[filename] || 0), 0);
    return Math.round(totalProgress / files.length);
  }

  // Estimate the remaining time based on progress and elapsed time
  getEstimatedTimeRemaining(): string {
    const pendingFiles = Object.keys(this.uploadStatus)
      .filter(filename => this.uploadStatus[filename] === 'pending');

    if (pendingFiles.length === 0) return 'Abgeschlossen';

    // Calculate average time per percentage point
    let totalTimePerPercent = 0;
    let validFileCount = 0;
    const MIN_PROGRESS_THRESHOLD = 5; // Minimum progress percentage to consider for calculation

    pendingFiles.forEach(filename => {
      const progress = this.uploadProgress[filename] || 0;
      const startTime = this.startTimes[filename];

      // Only consider files with progress above the threshold
      if (progress >= MIN_PROGRESS_THRESHOLD && startTime) {
        const elapsedTime = Date.now() - startTime; // in milliseconds
        const timePerPercent = elapsedTime / progress;

        // Ignore unreasonably high values (could happen with very low progress)
        if (timePerPercent < 10000) { // Max 10 seconds per percentage point
          totalTimePerPercent += timePerPercent;
          validFileCount++;
        }
      }
    });

    // Calculate new estimate
    let newEstimatedTime = 0;
    const overallProgress = this.getOverallProgress();

    // If we have valid progress data, use it for an accurate estimate
    if (validFileCount > 0) {
      const avgTimePerPercent = totalTimePerPercent / validFileCount;
      const remainingPercent = 100 - overallProgress;
      newEstimatedTime = avgTimePerPercent * remainingPercent;
    }
    // If we don't have valid progress data yet, provide a rough estimate
    else if (this.uploadStartTime > 0) {
      const elapsedTime = Date.now() - this.uploadStartTime;

      // Only show "Berechne..." for the first 2 seconds
      if (elapsedTime < 2000) {
        return 'Berechne...';
      }

      // Provide a rough estimate based on number of files and current progress
      const estimatedTotalTime = pendingFiles.length * 25000; // 25 seconds per file (slightly more conservative)
      newEstimatedTime = Math.max(estimatedTotalTime * (1 - overallProgress / 100) - elapsedTime, 1000);
    } else {
      // Fallback if we have no data at all
      return 'Berechne...';
    }

    // Apply smoothing using moving average
    if (this.lastEstimatedTime > 0) {
      // Prevent sudden large jumps (more than 50% change)
      const maxChange = this.lastEstimatedTime * 0.5;
      if (Math.abs(newEstimatedTime - this.lastEstimatedTime) > maxChange) {
        // Limit the change to 50% of the previous estimate
        newEstimatedTime = this.lastEstimatedTime + (newEstimatedTime > this.lastEstimatedTime ? maxChange : -maxChange);
      }

      // Add to history for moving average
      this.estimateHistory.push(newEstimatedTime);
      if (this.estimateHistory.length > this.MAX_HISTORY_LENGTH) {
        this.estimateHistory.shift(); // Remove oldest estimate
      }

      // Calculate moving average
      const sum = this.estimateHistory.reduce((a, b) => a + b, 0);
      newEstimatedTime = sum / this.estimateHistory.length;
    }

    // Update last estimated time
    this.lastEstimatedTime = newEstimatedTime;

    // Convert to readable format
    if (newEstimatedTime < 1000) {
      return 'Fast fertig';
    } else if (newEstimatedTime < 60000) {
      return `${Math.ceil(newEstimatedTime / 1000)} Sekunden`;
    } else if (newEstimatedTime < 3600000) {
      return `${Math.ceil(newEstimatedTime / 60000)} Minuten`;
    } else {
      const hours = Math.floor(newEstimatedTime / 3600000);
      const minutes = Math.ceil((newEstimatedTime % 3600000) / 60000);
      return `${hours} Stunden ${minutes} Minuten`;
    }
  }
}
