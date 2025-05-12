import { ComponentFixture, TestBed } from '@angular/core/testing';

import { XmlUploaderComponent } from './xml-uploader.component';

describe('XmlUploaderComponent', () => {
  let component: XmlUploaderComponent;
  let fixture: ComponentFixture<XmlUploaderComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [XmlUploaderComponent]
    });
    fixture = TestBed.createComponent(XmlUploaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
