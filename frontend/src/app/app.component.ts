import { Component, OnInit, OnDestroy } from '@angular/core';
import { XmlUploaderService } from './xml-uploader/xml-uploader.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'frontend';
  sidebarOpen = false;

  private subscriptions: Subscription[] = [];

  constructor(private uploaderService: XmlUploaderService) {}

  ngOnInit() {
    // No initialization needed
  }


  ngOnDestroy() {
    // Unsubscribe from all subscriptions
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }

  toggleSidebar() {
    this.sidebarOpen = !this.sidebarOpen;
  }
}
